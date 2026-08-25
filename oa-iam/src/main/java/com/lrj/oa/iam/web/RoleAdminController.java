package com.lrj.oa.iam.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.iam.api.dto.RoleAdminDtos.RoleDetail;
import com.lrj.oa.iam.api.dto.RoleAdminDtos.RoleSummary;
import com.lrj.oa.iam.application.RoleAdminService;
import com.lrj.oa.iam.application.command.IamCommands;
import com.lrj.oa.security.annotation.RequiresPerm;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/iam/role-admin")
@RequiresPerm("oa:iam:admin")
public class RoleAdminController {
    private final RoleAdminService service;

    public RoleAdminController(RoleAdminService service) { this.service = service; }

    @GetMapping
    public Result<List<RoleSummary>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(status, keyword));
    }

    @GetMapping("/{id}")
    public Result<RoleDetail> detail(@PathVariable long id) { return Result.ok(service.detail(id)); }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody IamCommands.CreateRole cmd) {
        return Result.ok(service.create(cmd));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable long id, @Valid @RequestBody IamCommands.UpdateRole cmd) {
        service.update(id, cmd);
        return Result.ok();
    }

    @PostMapping("/{id}/copy")
    public Result<Long> copy(@PathVariable long id, @Valid @RequestBody IamCommands.CopyRole cmd) {
        return Result.ok(service.copy(id, cmd));
    }

    @PostMapping("/{id}/enabled")
    public Result<Void> enabled(@PathVariable long id, @Valid @RequestBody IamCommands.SetRoleEnabled cmd) {
        service.setEnabled(id, cmd);
        return Result.ok();
    }

    @PutMapping("/{id}/permissions")
    public Result<Void> permissions(@PathVariable long id,
                                    @Valid @RequestBody IamCommands.ReplaceRolePermissions cmd) {
        service.replacePermissions(id, cmd);
        return Result.ok();
    }

    @PutMapping("/{id}/inheritance")
    public Result<Void> inheritance(@PathVariable long id,
                                    @Valid @RequestBody IamCommands.ReplaceRoleInheritance cmd) {
        service.replaceInheritance(id, cmd);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id, @RequestParam int version) {
        service.delete(id, version);
        return Result.ok();
    }
}
