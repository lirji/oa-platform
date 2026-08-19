package com.lrj.oa.flow.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.flow.application.OutboxPublisher;
import com.lrj.oa.flow.infrastructure.workflow.WorkflowGateway;
import com.lrj.oa.security.annotation.RequiresPerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 审批域运维：发件箱健康度与当前接的是谁。 */
@RestController
@RequestMapping("/api/v1/flow/admin")
public class FlowAdminController {

    private final OutboxPublisher outboxPublisher;
    private final WorkflowGateway gateway;

    public FlowAdminController(OutboxPublisher outboxPublisher, WorkflowGateway gateway) {
        this.outboxPublisher = outboxPublisher;
        this.gateway = gateway;
    }

    @GetMapping("/status")
    @RequiresPerm("oa:flow:admin")
    public Result<Map<String, Object>> status() {
        Map<String, Object> out = new LinkedHashMap<>(outboxPublisher.stats());
        out.put("workflowMode", gateway.remote() ? "REMOTE(workflow-platform)" : "LOCAL(测试替身)");
        return Result.ok(out);
    }
}
