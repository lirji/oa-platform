package com.lrj.oa.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OA 平台主应用（模块化单体，:8400）。
 *
 * <p>装配 org / iam / flow / attendance / doc / admin / report 七个业务模块。
 * 通知推送、文件、跑批三个服务独立部署（伸缩曲线与主应用不同，见 ADR-0002）。
 */
@SpringBootApplication(scanBasePackages = "com.lrj.oa")
public class OaApplication {
    public static void main(String[] args) {
        SpringApplication.run(OaApplication.class, args);
    }
}
