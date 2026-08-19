package com.lrj.oa.notify;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 通知推送服务：WebSocket 长连 + 站内信 + 多渠道推送。按【连接数】而非 QPS 扩容，故独立部署。端口 :8401。
 *
 * <p><b>为什么要扫 oa.iam / oa.org</b>：判权是本地内存判定（零远程调用，P99 &lt; 1ms），
 * 所以每个可部署单元都得自带引擎。只带注解不带切面的话，{@code @RequiresPerm} 就是个装饰品。
 *
 * <p><b>为什么要把它们的 web 层排除掉</b>：oa-iam 里有授权管理、权限调试器等管理接口，
 * 一并扫进来等于在 :8401 上又开了一套 IAM 管理面 —— 多一个暴露面，就多一处要防的地方。
 * 通知服务只需要 iam 的<b>判权能力</b>，不需要它的<b>管理接口</b>。
 */
@SpringBootApplication(scanBasePackages = {
        "com.lrj.oa.notify", "com.lrj.oa.common", "com.lrj.oa.security",
        "com.lrj.oa.iam", "com.lrj.oa.org"
})
@ComponentScan(
        basePackages = {"com.lrj.oa.notify", "com.lrj.oa.common", "com.lrj.oa.security",
                "com.lrj.oa.iam", "com.lrj.oa.org"},
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = {"com\\.lrj\\.oa\\.iam\\.web\\..*", "com\\.lrj\\.oa\\.org\\.web\\..*"}),
                // 引导管理员只应由主应用做一次；每个服务都跑一遍会重复写授权并各自 bump 失效
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "com\\.lrj\\.oa\\.iam\\.application\\.BootstrapAdminInitializer")
        })
@MapperScan("com.lrj.oa.**.infrastructure.mapper")
@EnableConfigurationProperties(NotifyProperties.class)
@EnableScheduling
public class OaNotifyApplication {
    public static void main(String[] args) {
        SpringApplication.run(OaNotifyApplication.class, args);
    }
}
