package com.lrj.oa.iam.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.iam.application.AbacPolicyService;
import com.lrj.oa.iam.application.command.IamCommands;
import com.lrj.oa.iam.domain.PermissionCondition;
import com.lrj.oa.security.annotation.RequiresPerm;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/iam/abac")
@RequiresPerm("oa:iam:admin")
public class AbacPolicyController {
    private final AbacPolicyService service;
    public AbacPolicyController(AbacPolicyService service) { this.service = service; }

    @GetMapping("/conditions")
    public Result<List<PermissionCondition>> list(@RequestParam(required = false) Long roleId,
                                                  @RequestParam(required = false) Long permissionId) {
        return Result.ok(service.list(roleId, permissionId));
    }

    @PostMapping("/conditions")
    public Result<Long> create(@Valid @RequestBody IamCommands.AbacCondition cmd) {
        return Result.ok(service.create(cmd));
    }

    @PutMapping("/conditions/{id}")
    public Result<Void> update(@PathVariable long id, @Valid @RequestBody IamCommands.AbacCondition cmd) {
        service.update(id, cmd);
        return Result.ok();
    }

    @PostMapping("/conditions/{id}/enabled")
    public Result<Void> enabled(@PathVariable long id, @RequestParam boolean enabled) {
        service.setEnabled(id, enabled);
        return Result.ok();
    }

    @DeleteMapping("/conditions/{id}")
    public Result<Void> delete(@PathVariable long id) {
        service.delete(id);
        return Result.ok();
    }

    @PostMapping("/validate")
    public Result<Map<String, Boolean>> validate(@Valid @RequestBody IamCommands.ValidateAbac cmd) {
        service.validateExpression(cmd.expression());
        return Result.ok(Map.of("valid", true));
    }
}
