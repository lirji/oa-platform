package com.lrj.oa.notify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 通知推送服务：WebSocket 长连 + 站内信 + 多渠道推送。按【连接数】而非 QPS 扩容，故独立部署。 端口 :8401。 */
@SpringBootApplication(scanBasePackages = {"com.lrj.oa.notify", "com.lrj.oa.common", "com.lrj.oa.security"})
public class OaNotifyApplication {
    public static void main(String[] args) {
        SpringApplication.run(OaNotifyApplication.class, args);
    }
}
