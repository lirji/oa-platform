package com.lrj.oa.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 跑批服务：考勤月结 / 报表预计算 / 授权到期回收。分片调度，与在线请求隔离 CPU。 端口 :8403。 */
@SpringBootApplication(scanBasePackages = {"com.lrj.oa.job", "com.lrj.oa.common", "com.lrj.oa.security"})
public class OaJobApplication {
    public static void main(String[] args) {
        SpringApplication.run(OaJobApplication.class, args);
    }
}
