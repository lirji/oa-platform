package com.lrj.oa.flow.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.flow.application.BusinessDocService;
import com.lrj.oa.security.annotation.RequiresPerm;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 通用业务单据 REST：出差 / 加班 / 调休 / 报销 / 借款 / 用印 / 合同 / 采购 / 入职 / 离职 / 调岗。
 *
 * <p>一组接口服务 11 类单据 —— 类型由 bizType 决定，字段由 form_template 描述，
 * 审批级数由 approval_level_rule 决定。加一类新单据只需要插两行数据，不用发版。
 */
@RestController
@RequestMapping("/api/v1/flow/docs")
public class BusinessDocController {

    private final BusinessDocService service;

    public BusinessDocController(BusinessDocService service) { this.service = service; }

    /** 支持的单据类型 + 各自的 JSON Schema。前端据此渲染表单，不必为每类写一个页面。 */
    @GetMapping("/types")
    @RequiresPerm("oa:doc-flow:submit")
    public Result<List<Map<String, Object>>> types() { return Result.ok(service.supportedTypes()); }

    @PostMapping
    @RequiresPerm("oa:doc-flow:submit")
    public Result<BusinessDocService.DocView> submit(@RequestBody BusinessDocService.SubmitDoc cmd) {
        return Result.ok(service.submit(cmd));
    }

    @GetMapping("/mine")
    @RequiresPerm("oa:doc-flow:submit")
    public Result<List<BusinessDocService.DocView>> mine(@RequestParam(required = false) String bizType,
                                                         @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(service.myDocs(bizType, limit));
    }
}
