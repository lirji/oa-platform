package com.lrj.oa.app;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * <b>Phase 0 硬门禁 A</b> —— JDK21 虚拟线程 pinning 实测。
 *
 * <p>背景（FINAL_PLAN R1）：虚拟线程遇到 {@code synchronized} 会 <b>pin 住载体线程</b>，
 * 高并发下会把 ForkJoinPool 的载体线程耗尽，退化得比平台线程还差。
 * HikariCP 与部分 MyBatis 版本内部有 synchronized 块 —— 到底会不会 pin，
 * <b>只能实测，不能靠读文档下结论</b>。这个门禁不过，就把 {@code spring.threads.virtual.enabled} 关掉。
 *
 * <p>探针本身用 JFR 的 {@code jdk.VirtualThreadPinned} 事件计数。
 * <b>关键：先跑对照组</b> —— 故意制造 pinning 并断言探针能测到。
 * 一个测不出 pinning 的探针报"0 次 pinning"是毫无价值的，这是本测试最重要的一半。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Phase0PinningProbeTest {

    private static final String JDBC_URL = System.getProperty("oa.probe.jdbc",
            "jdbc:postgresql://localhost:35432/oa");
    private static final String DB_USER = System.getProperty("oa.probe.user", "oa");
    private static final String DB_PASSWORD = System.getProperty("oa.probe.password", "oa_dev_pwd");

    private static final int VIRTUAL_THREADS = 2_000;
    private static final int POOL_SIZE = 20;

    // ---------------------------------------------------------------- 对照组

    @Test
    @Order(1)
    @DisplayName("对照组：故意 synchronized + sleep，探针必须能测到 pinning（证明探针有效）")
    void probe_actually_detects_pinning() throws Exception {
        Object lock = new Object();

        long pinned = countPinnedDuring(() -> {
            try (ExecutorService vexec = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < 8; i++) {
                    vexec.submit(() -> {
                        synchronized (lock) {
                            sleepQuietly(60);       // 在 synchronized 里阻塞 = 必然 pin 载体线程
                        }
                    });
                }
            }
        });

        System.out.printf("[对照组] 故意制造的 pinning 事件数 = %d%n", pinned);
        assertThat(pinned)
                .as("探针失效：故意 pin 都测不出来，那么后面'0 次 pinning'的结论就是假的")
                .isGreaterThan(0);
    }

    // ---------------------------------------------------------------- 真实负载

    @Test
    @Order(2)
    @DisplayName("硬门禁 A：HikariCP + JDBC 在 2000 虚拟线程下不应出现 pinning")
    void hikari_jdbc_under_virtual_threads_does_not_pin() throws Exception {
        assumeTrue(postgresReachable(),
                "PostgreSQL " + JDBC_URL + " 不可达 —— 先跑 deploy/scripts/phase0-smoke.sh 起 compose");

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(JDBC_URL);
        cfg.setUsername(DB_USER);
        cfg.setPassword(DB_PASSWORD);
        cfg.setMaximumPoolSize(POOL_SIZE);
        cfg.setPoolName("oa-pinning-probe");

        AtomicInteger ok = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        long[] elapsed = new long[1];

        try (HikariDataSource ds = new HikariDataSource(cfg)) {
            long pinned = countPinnedDuring(() -> {
                long t0 = System.nanoTime();
                CountDownLatch done = new CountDownLatch(VIRTUAL_THREADS);
                try (ExecutorService vexec = Executors.newVirtualThreadPerTaskExecutor()) {
                    for (int i = 0; i < VIRTUAL_THREADS; i++) {
                        vexec.submit(() -> {
                            try (Connection c = ds.getConnection();
                                 Statement st = c.createStatement();
                                 ResultSet rs = st.executeQuery("SELECT 1")) {
                                if (rs.next()) ok.incrementAndGet();
                            } catch (Exception e) {
                                failed.incrementAndGet();
                            } finally {
                                done.countDown();
                            }
                        });
                    }
                }
                try { done.await(60, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
                elapsed[0] = (System.nanoTime() - t0) / 1_000_000;
            });

            System.out.printf(
                    "[硬门禁A] 虚拟线程=%d 连接池=%d 成功=%d 失败=%d 耗时=%dms pinning事件=%d%n",
                    VIRTUAL_THREADS, POOL_SIZE, ok.get(), failed.get(), elapsed[0], pinned);

            assertThat(failed.get()).as("查询失败数").isZero();
            assertThat(ok.get()).as("成功查询数").isEqualTo(VIRTUAL_THREADS);
            assertThat(pinned)
                    .as("""
                        HikariCP/JDBC 在虚拟线程下出现了 pinning。
                        处置：关掉 spring.threads.virtual.enabled 回退平台线程池，
                        并把实测数据写进 IMPLEMENTATION_PROGRESS.md 的硬门禁 A 结论。""")
                    .isZero();
        }
    }

    // ---------------------------------------------------------------- 探针实现

    /** 在 JFR 记录窗口内跑一段负载，返回窗口内 jdk.VirtualThreadPinned 事件数。 */
    private static long countPinnedDuring(Runnable workload) throws IOException {
        Path dump = Files.createTempFile("oa-pinning-probe-", ".jfr");
        try (Recording r = new Recording()) {
            r.enable("jdk.VirtualThreadPinned").withThreshold(Duration.ofMillis(1));
            r.setToDisk(true);
            r.start();
            try {
                workload.run();
            } finally {
                r.stop();
                r.dump(dump);
            }

            long count = 0;
            try (RecordingFile rf = new RecordingFile(dump)) {
                while (rf.hasMoreEvents()) {
                    if ("jdk.VirtualThreadPinned".equals(rf.readEvent().getEventType().getName())) count++;
                }
            }
            return count;
        } finally {
            Files.deleteIfExists(dump);
        }
    }

    private static boolean postgresReachable() {
        try (Connection c = java.sql.DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD)) {
            return c.isValid(3);
        } catch (Exception e) {
            return false;
        }
    }

    private static void sleepQuietly(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
