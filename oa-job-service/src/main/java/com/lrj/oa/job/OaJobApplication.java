package com.lrj.oa.job;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 跑批服务 :8403。分片调度 + 授权到期回收 + 考勤日结 + 分区滚动。
 *
 * <p>独立部署的理由与 notify 不同：notify 按<b>连接数</b>扩容，job 是为了让
 * <b>夜间重负载不与在线请求抢资源</b> —— 月结要扫 30 万行，跟打卡挤在同一个 JVM 里
 * 会让早高峰的 GC 停顿变得不可预测。
 *
 * <p>同样嵌判权引擎（管理接口要鉴权），同样排除 iam/org 的 web 层。
 */
@SpringBootApplication
@ComponentScan(
        basePackages = {"com.lrj.oa.job", "com.lrj.oa.common", "com.lrj.oa.security",
                "com.lrj.oa.iam", "com.lrj.oa.org", "com.lrj.oa.attendance"},
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = {"com\\.lrj\\.oa\\.iam\\.web\\..*", "com\\.lrj\\.oa\\.org\\.web\\..*",
                                "com\\.lrj\\.oa\\.attendance\\.web\\..*"}),
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "com\\.lrj\\.oa\\.iam\\.application\\.BootstrapAdminInitializer")
        })
@MapperScan("com.lrj.oa.**.infrastructure.mapper")
@EnableScheduling
public class OaJobApplication {
    public static void main(String[] args) {
        SpringApplication.run(OaJobApplication.class, args);
    }
}
