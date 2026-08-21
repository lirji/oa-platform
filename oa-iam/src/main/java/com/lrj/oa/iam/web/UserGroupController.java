package com.lrj.oa.iam.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.iam.application.UserGroupService;
import com.lrj.oa.iam.application.command.IamCommands;
import com.lrj.oa.iam.domain.UserGroup;
import com.lrj.oa.iam.domain.UserGroupMember;
import com.lrj.oa.security.annotation.RequiresPerm;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/iam/groups")
@RequiresPerm("oa:iam:admin")
public class UserGroupController {
    private final UserGroupService service;
    public UserGroupController(UserGroupService service) { this.service = service; }

    @GetMapping
    public Result<List<UserGroup>> list(@RequestParam(required = false) String status) {
        return Result.ok(service.list(status));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody IamCommands.CreateGroup cmd) {
        return Result.ok(service.create(cmd));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable long id, @Valid @RequestBody IamCommands.UpdateGroup cmd) {
        service.update(id, cmd);
        return Result.ok();
    }

    @PostMapping("/{id}/enabled")
    public Result<Void> enabled(@PathVariable long id, @RequestParam boolean enabled) {
        service.setEnabled(id, enabled);
        return Result.ok();
    }

    @GetMapping("/{id}/members")
    public Result<List<UserGroupMember>> members(@PathVariable long id) {
        return Result.ok(service.members(id));
    }

    @PostMapping("/{id}/members")
    public Result<Map<String, Integer>> addMembers(@PathVariable long id,
                                                   @Valid @RequestBody IamCommands.AddGroupMembers cmd) {
        return Result.ok(Map.of("affected", service.addMembers(id, cmd)));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> removeMember(@PathVariable long id, @PathVariable String userId) {
        service.removeMember(id, userId);
        return Result.ok();
    }
}
