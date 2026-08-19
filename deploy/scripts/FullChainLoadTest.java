import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全链路压测（Phase 8 验收）。
 *
 * <p>用法：{@code java FullChainLoadTest <baseUrl> <requestsPerScenario> <concurrency>}
 *
 * <p><b>为什么不是 k6</b>：计划里写的是 k6，但本机没有装，而项目在 Phase 5 已经
 * 用单文件 Java 程序（{@code PunchLoadTest.java}）解决过同一件事 —— 虚拟线程发压对
 * JDK21 是廉价的，不值得为压测再引一个需要单独安装的工具链。
 * 这个偏离是有意的，记在这里免得以后有人以为是漏做了。
 *
 * <p><b>量的是什么</b>：几条真实读路径的服务端延迟分布。刻意用<b>较低并发</b>——
 * 高并发下的 P99 量的是客户端排队而不是服务端能力（Little 定律，Phase 5 的教训：
 * 300 并发下 P99=1469ms 看着很差，压到 20 并发后 P50=3ms/P99=53ms 才是真实成本）。
 *
 * <p>验收线：工作台首屏 API P99 &lt; 200 ms。
 */
public class FullChainLoadTest {

    /** base 允许每个场景不同：公告在通知服务 :8401，其余在主应用 :8400。 */
    record Scenario(String name, String base, String path, String user, long p99BudgetMs) {}

    public static void main(String[] args) throws Exception {
        String base = args.length > 0 ? args[0] : "http://localhost:8400";
        int n = args.length > 1 ? Integer.parseInt(args[1]) : 500;
        int concurrency = args.length > 2 ? Integer.parseInt(args[2]) : 20;
        String admin = System.getenv().getOrDefault("OA_ADMIN", "seed-user-1");

        String notify = System.getenv().getOrDefault("OA_NOTIFY_BASE", "http://localhost:8401");
        List<Scenario> scenarios = List.of(
                // 工作台首屏：验收线明确写了 P99 < 200ms
                new Scenario("工作台待办", base, "/api/v1/flow/todos", admin, 200),
                new Scenario("我的权限清单", base, "/api/v1/me/permissions", admin, 200),
                // 判权热路径：每个受保护接口都要过一次判权，这条最能反映引擎开销
                new Scenario("通讯录分页", base, "/api/v1/org/directory?page=0&size=20", admin, 200),
                // 组织树是内存快照（COW），不查库——它和通讯录一起能区分"判权开销"与"查库开销"
                new Scenario("组织树", base, "/api/v1/org/units/tree?depth=3", admin, 200),
                new Scenario("驾驶舱概览", base, "/api/v1/report/overview", admin, 500),
                new Scenario("公告列表", notify, "/api/v1/announcements?limit=20", admin, 200));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

        System.out.printf("全链路压测：每个场景 %d 次请求，并发 %d%n", n, concurrency);
        System.out.println("（并发刻意压低——高并发下的 P99 量的是客户端排队，不是服务端能力）");
        System.out.println();
        System.out.printf("%-16s %8s %8s %8s %8s %8s %10s  %s%n",
                "场景", "成功", "失败", "P50ms", "P95ms", "P99ms", "QPS", "判定");

        int violations = 0;
        for (Scenario sc : scenarios) {
            Result r = run(client, base, sc, n, concurrency);
            boolean pass = r.fail == 0 && r.p99 <= sc.p99BudgetMs();
            if (!pass) violations++;
            System.out.printf("%-16s %8d %8d %8.1f %8.1f %8.1f %10.0f  %s%n",
                    sc.name(), r.ok, r.fail, r.p50, r.p95, r.p99, r.qps,
                    pass ? "✅" : ("❌ 超出 " + sc.p99BudgetMs() + "ms 预算"));
        }
        System.out.println();
        if (violations == 0) {
            System.out.println("全部场景在预算内 ✅");
        } else {
            System.out.println(violations + " 个场景超预算 ❌");
            System.exit(1);
        }
    }

    record Result(int ok, int fail, double p50, double p95, double p99, double qps) {}

    static Result run(HttpClient client, String base, Scenario sc, int n, int concurrency)
            throws Exception {
        AtomicInteger ok = new AtomicInteger(), fail = new AtomicInteger();
        long[] lat = new long[n];
        Semaphore gate = new Semaphore(concurrency);
        CountDownLatch done = new CountDownLatch(n);

        // 预热：JIT 与连接池没热起来时的头几次请求会严重拉高 P99，
        // 量的是"冷启动"而不是"稳态"。预热请求不计入统计。
        for (int i = 0; i < Math.min(20, n); i++) {
            try {
                client.send(req(base, sc), HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
                // 预热失败不影响正式统计
            }
        }

        long t0 = System.nanoTime();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < n; i++) {
                final int idx = i;
                pool.submit(() -> {
                    try {
                        gate.acquire();
                        long s = System.nanoTime();
                        HttpResponse<Void> resp =
                                client.send(req(base, sc), HttpResponse.BodyHandlers.discarding());
                        lat[idx] = System.nanoTime() - s;
                        if (resp.statusCode() == 200) ok.incrementAndGet(); else fail.incrementAndGet();
                    } catch (Exception e) {
                        fail.incrementAndGet();
                    } finally {
                        gate.release();
                        done.countDown();
                    }
                });
            }
            done.await(5, TimeUnit.MINUTES);
        }
        long elapsedNs = System.nanoTime() - t0;

        long[] sorted = Arrays.stream(lat).filter(v -> v > 0).sorted().toArray();
        return new Result(ok.get(), fail.get(),
                pct(sorted, 50), pct(sorted, 95), pct(sorted, 99),
                n / (elapsedNs / 1_000_000_000.0));
    }

    static HttpRequest req(String base, Scenario sc) {
        return HttpRequest.newBuilder(URI.create(sc.base() + sc.path()))
                .header("X-OA-User", sc.user())
                .timeout(Duration.ofSeconds(20))
                .GET().build();
    }

    static double pct(long[] sortedNs, int p) {
        if (sortedNs.length == 0) return 0;
        int i = (int) Math.ceil(p / 100.0 * sortedNs.length) - 1;
        return sortedNs[Math.max(0, Math.min(i, sortedNs.length - 1))] / 1_000_000.0;
    }
}
