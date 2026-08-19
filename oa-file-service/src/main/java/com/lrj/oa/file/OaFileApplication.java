package com.lrj.oa.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 文件服务：上传/下载/预览 + MinIO。大流量 IO，独立部署以免挤占主应用线程与带宽。 端口 :8402。 */
@SpringBootApplication(scanBasePackages = {"com.lrj.oa.file", "com.lrj.oa.common", "com.lrj.oa.security"})
public class OaFileApplication {
    public static void main(String[] args) {
        SpringApplication.run(OaFileApplication.class, args);
    }
}
