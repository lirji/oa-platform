import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 早高峰打卡压测。
 *
 * <p>用法：{@code java PunchLoadTest <baseUrl> <userCount> <concurrency> [IN|OUT]}
 *
 * <p>模拟的是真实形态：{@code userCount} 个<b>不同的人</b>在同一时刻打卡，
 * 而不是一个人狂打 —— 后者会被 Redis 幂等键在第一跳就挡掉，压的是幂等而不是链路。
 *
 * <p>用虚拟线程发压：几千个并发连接对 JDK21 来说是廉价的，
 * 不需要为压测再引一个框架。
 */
public class PunchLoadTest {

    public static void main(String[] args) throws Exception {
        String base = args.length > 0 ? args[0] : "http://localhost:8400";
        int users = args.length > 1 ? Integer.parseInt(args[1]) : 10_000;
        int concurrency = args.length > 2 ? Integer.parseInt(args[2]) : 200;
        String type = args.length > 3 ? args[3] : "IN";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

        AtomicInteger ok = new AtomicInteger(), dup = new AtomicInteger(), fail = new AtomicInteger();
        long[] latencies = new long[users];
        Semaphore gate = new Semaphore(concurrency);
        CountDownLatch done = new CountDownLatch(users);

        System.out.printf("压测开始：%d 人打%s卡，并发上限 %d%n", users, type, concurrency);
        long t0 = System.nanoTime();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < users; i++) {
                final int idx = i;
                pool.submit(() -> {
                    try {
                        gate.acquire();
                        long s = System.nanoTime();
                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create(base + "/api/v1/attendance/punch?type=" + type + "&source=MOBILE"))
                                .header("X-OA-User", "seed-user-" + (idx + 1))
                                .timeout(Duration.ofSeconds(20))
                                .POST(HttpRequest.BodyPublishers.noBody())
                                .build();
                        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                        latencies[idx] = System.nanoTime() - s;
                        if (resp.statusCode() != 200)            fail.incrementAndGet();
                        else if (resp.body().contains("\"duplicate\":true")) dup.incrementAndGet();
                        else if (resp.body().contains("\"code\":0")) ok.incrementAndGet();
                        else                                      fail.incrementAndGet();
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

        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        long[] sorted = Arrays.stream(latencies).filter(v -> v > 0).sorted().toArray();
        double qps = users * 1000.0 / Math.max(elapsedMs, 1);

        System.out.printf("RESULT users=%d ok=%d dup=%d fail=%d elapsedMs=%d qps=%.0f p50ms=%.1f p99ms=%.1f%n",
                users, ok.get(), dup.get(), fail.get(), elapsedMs, qps,
                sorted.length == 0 ? 0 : sorted[(int) (sorted.length * 0.50)] / 1e6,
                sorted.length == 0 ? 0 : sorted[(int) (sorted.length * 0.99)] / 1e6);
    }
}
