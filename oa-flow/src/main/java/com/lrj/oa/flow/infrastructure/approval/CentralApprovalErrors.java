package com.lrj.oa.flow.infrastructure.approval;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

/** 服务间契约保留真实HTTP状态；旧全局业务错误包装不能把404变成成功或500。 */
@RestControllerAdvice(assignableTypes=CentralApprovalController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CentralApprovalErrors {
    /** 已知边界错误只返回稳定编码，不把请求快照或异常堆栈带出服务。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> known(ResponseStatusException failure) {
        return ResponseEntity.status(failure.getStatusCode()).body(Map.of("code","APPROVAL_REQUEST_REJECTED"));
    }
    /** 未知失败保留未知结果语义，投递方只能按业务键查询恢复。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,String>> unavailable(Exception failure) {
        return ResponseEntity.status(503).body(Map.of("code","APPROVAL_DEPENDENCY_UNAVAILABLE"));
    }
}
