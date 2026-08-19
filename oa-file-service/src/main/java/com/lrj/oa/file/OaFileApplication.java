package com.lrj.oa.file;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 文件服务 :8402。上传 / 下载 / 预签名 + MinIO。
 *
 * <p>独立部署的理由：大文件传输是<b>带宽与内存</b>密集的，跟在线请求挤在一个 JVM 里，
 * 一次 50MB 上传就能把主应用的堆和网卡一起吃掉。
 *
 * <p>同样嵌判权引擎 —— 文件下载是 IDOR 的重灾区，注解摆着没人执行比不写更危险。
 */
@SpringBootApplication
@ComponentScan(
        basePackages = {"com.lrj.oa.file", "com.lrj.oa.common", "com.lrj.oa.security",
                "com.lrj.oa.iam", "com.lrj.oa.org"},
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = {"com\\.lrj\\.oa\\.iam\\.web\\..*", "com\\.lrj\\.oa\\.org\\.web\\..*"}),
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "com\\.lrj\\.oa\\.iam\\.application\\.BootstrapAdminInitializer")
        })
@MapperScan("com.lrj.oa.**.infrastructure.mapper")
public class OaFileApplication {
    public static void main(String[] args) {
        SpringApplication.run(OaFileApplication.class, args);
    }
}
