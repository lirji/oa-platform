package com.lrj.oa.app.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.security.annotation.PublicApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class HealthController {

    private final String version;

    public HealthController(@Value("${oa.version:0.1.0-SNAPSHOT}") String version) {
        this.version = version;
    }

    @GetMapping("/ping")
    @PublicApi(reason = "存活探针，不含任何业务数据，供 compose/k8s 与冒烟脚本调用")
    public Result<Map<String, Object>> ping() {
        return Result.ok(Map.of(
                "app", "oa-platform",
                "version", version,
                "virtualThreads", Thread.currentThread().isVirtual()
        ));
    }
}
