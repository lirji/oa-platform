package com.lrj.oa.org.api;

import com.lrj.oa.org.application.directory.IdentityDirectoryExport;
import com.lrj.oa.org.infrastructure.directory.IdentityDirectorySecurityConfiguration;
import com.lrj.oa.security.annotation.RequiresServiceIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 仅面向治理拉取器的版本化契约，不暴露来源登记、任意企业选择或员工资料。 */
@RestController
@RequestMapping("/internal/directory/v1")
@RequiresServiceIdentity(IdentityDirectorySecurityConfiguration.SERVICE)
public class IdentityDirectoryController {
    private final IdentityDirectoryExport directory;
    public IdentityDirectoryController(IdentityDirectoryExport directory) { this.directory = directory; }
    /** 来源身份由专用安全链提供，游标与页长均有硬上限。 */
    @GetMapping("/events")
    public IdentityDirectoryExport.Page events(@RequestParam("after_sequence") long after, @RequestParam(defaultValue = "100") int limit) {
        return directory.events(after, limit);
    }
    /** 运行状态只涉及固定来源的积压与确认水位。 */
    @GetMapping("/status")
    public IdentityDirectoryExport.Status status() { return directory.status(); }
    /** 确认正文严格限定字段、类型与大小，不能借宽松 JSON 转换接受浮点/字符串水位。 */
    @PostMapping("/ack")
    public IdentityDirectoryExport.Status acknowledge(HttpServletRequest request) throws IOException {
        var acknowledgement = IdentityDirectoryAcknowledgement.parse(request.getInputStream().readNBytes(1025));
        return directory.acknowledge(acknowledgement.sequence(), acknowledgement.fingerprint());
    }

    /** 内部协议保持 HTTP 非成功语义，避免被面向页面的全局异常包装降为不明确的业务响应。 */
    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<java.util.Map<String, String>> protocolError(ResponseStatusException failure) {
        return org.springframework.http.ResponseEntity.status(failure.getStatusCode()).body(java.util.Map.of("error", "directory_request_rejected"));
    }

    /** 参数转换错误不回显用户输入，也不能变成可重试的 500。 */
    @ExceptionHandler({org.springframework.web.bind.ServletRequestBindingException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public org.springframework.http.ResponseEntity<java.util.Map<String, String>> invalidParameter(Exception failure) {
        return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of("error", "directory_request_invalid"));
    }
}
