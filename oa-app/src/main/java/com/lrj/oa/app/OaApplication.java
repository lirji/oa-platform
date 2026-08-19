package com.lrj.oa.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * OA 平台主应用（模块化单体，:8400）。
 *
 * <p>装配 org / iam / flow / attendance / doc / admin / report 七个业务模块。
 * 通知推送、文件、跑批三个服务独立部署（伸缩曲线与主应用不同，见 ADR-0002）。
 *
 * <p>Mapper 扫描用通配 {@code com.lrj.oa.**.infrastructure.mapper}：
 * 各业务模块自己管自己的 mapper 包，新增模块不需要回来改这里。
 */
@SpringBootApplication(scanBasePackages = "com.lrj.oa")
@MapperScan("com.lrj.oa.**.infrastructure.mapper")
@EnableScheduling
public class OaApplication {
    public static void main(String[] args) {
        SpringApplication.run(OaApplication.class, args);
    }
}
