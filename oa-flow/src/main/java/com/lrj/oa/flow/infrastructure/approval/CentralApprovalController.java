package com.lrj.oa.flow.infrastructure.approval;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.lrj.authz.protocol.ApprovalDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 仅解析通过原文验签的缓存字节，不从普通用户body接受服务身份。 */
@RestController
@ConditionalOnProperty(name="oa.flow.central-approval.enabled",havingValue="true")
@RequestMapping("/internal/iam-approval/v1")
@com.lrj.oa.security.annotation.RequiresServiceIdentity("auth-platform")
public class CentralApprovalController {
    private final CentralApprovalService service;
    /** 控制器不直接创建流程或操作其他服务的数据库。 */
    public CentralApprovalController(CentralApprovalService service) { this.service=service; }
    /** 同一申请业务键返回既有实例，改体冲突不会产生第二条流程。 */
    @PostMapping(value="/start",consumes="application/json")
    public JsonNode start(HttpServletRequest request) { return CentralApprovalWire.body(service.start(read(request,Start.class))); }
    /** 网络未知结果恢复入口，空结果必须真实404而非吞异常。 */
    @PostMapping(value="/lookup",consumes="application/json")
    public JsonNode lookup(HttpServletRequest request) { return CentralApprovalWire.body(service.lookup(read(request,Lookup.class))); }
    private static <T>T read(HttpServletRequest request,Class<T> type) {
        try {
            com.lrj.oa.security.context.ServiceIdentity.require("auth-platform");
            Object body=request.getAttribute(CentralApprovalSecurity.BODY);
            if(!(body instanceof byte[] bytes)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            T value=CentralApprovalWire.read(bytes,type);
            if(value==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            return value;
        } catch(java.io.IOException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"APPROVAL_INVALID"); }
    }
}
