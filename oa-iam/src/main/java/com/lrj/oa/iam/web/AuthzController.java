package com.lrj.oa.iam.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.AuthzDtos.CheckDecision;
import com.lrj.oa.iam.api.dto.AuthzDtos.CheckRequest;
import com.lrj.oa.iam.api.dto.AuthzDtos.DecisionPage;
import com.lrj.oa.iam.application.AuthzCheckService;
import com.lrj.oa.security.annotation.RequiresPerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 统一授权判定与决策审计查询。热路径 {@code @RequiresPerm} 不写本表。 */
@RestController
@RequestMapping("/api/v1")
public class AuthzController {

    private final AuthzCheckService authz;

    public AuthzController(AuthzCheckService authz) {
        this.authz = authz;
    }

    @PostMapping("/authz/check")
    @RequiresPerm(value = {"oa:iam:check", "oa:iam:admin"}, logical = RequiresPerm.Logical.OR)
    public Result<CheckDecision> check(@RequestBody CheckRequest body) {
        return Result.ok(authz.check(body));
    }

    @GetMapping("/iam/admin/decisions")
    @RequiresPerm("oa:iam:admin")
    public Result<DecisionPage> decisions(@RequestParam(required = false) String identityId,
                                          @RequestParam(required = false) String decision,
                                          @RequestParam(required = false) String cursor,
                                          @RequestParam(defaultValue = "50") int size) {
        Long seq = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                seq = Long.parseLong(cursor);
            } catch (NumberFormatException ex) {
                throw BusinessException.of(ResultCode.BAD_REQUEST, "cursor 必须是数字序号");
            }
        }
        return Result.ok(authz.list(identityId, decision, seq, size));
    }
}
