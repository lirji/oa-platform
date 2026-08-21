import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 通知长连探针。单文件程序，{@code java WsProbe.java <url> <userId|ticket=...> <期望消息数> <超时秒> [Origin]}，无需构建。
 *
 * <p>存在理由：curl 不会说 WebSocket，而"公告推没推到在线用户"这件事
 * 只能真连上去看。冒烟脚本靠它把长连这条路径也纳入断言，
 * 否则 WebSocket 就成了唯一没人验过的通道。
 *
 * <p>输出格式固定为 {@code RECEIVED=<n>}，供 shell 断言。
 */
public class WsProbe {

    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0] : "ws://localhost:8401/ws";
        String identity = args.length > 1 ? args[1] : "seed-user-1";
        int expect = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        int timeoutSec = args.length > 3 ? Integer.parseInt(args[3]) : 20;
        String origin = args.length > 4 ? args[4] : "";
        String query = identity.startsWith("ticket=") || identity.startsWith("access_token=")
                ? identity
                : "userId=" + URLEncoder.encode(identity, StandardCharsets.UTF_8);

        AtomicInteger received = new AtomicInteger();
        // WELCOME 不计入期望消息数：它是连接确认，不是业务推送。
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(expect);

        WebSocket.Builder builder = HttpClient.newHttpClient().newWebSocketBuilder();
        if (!origin.isBlank()) builder.header("Origin", origin);
        WebSocket ws = builder
                .buildAsync(URI.create(url + (url.contains("?") ? "&" : "?") + query), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        String s = data.toString();
                        if (s.contains("\"WELCOME\"")) {
                            connected.countDown();
                        } else if (!s.contains("\"PONG\"")) {
                            received.incrementAndGet();
                            done.countDown();
                        }
                        webSocket.request(1);
                        return null;
                    }
                }).join();

        if (!connected.await(5, TimeUnit.SECONDS)) {
            ws.abort();
            throw new IllegalStateException("WebSocket 未收到 WELCOME");
        }
        System.out.println("CONNECTED");
        System.out.flush();

        done.await(timeoutSec, TimeUnit.SECONDS);
        System.out.println("RECEIVED=" + received.get());
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
    }
}
