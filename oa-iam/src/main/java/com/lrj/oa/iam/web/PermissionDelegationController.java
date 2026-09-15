package com.lrj.oa.iam.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.PermissionDelegationDtos.CreatePermissionDelegation;
import com.lrj.oa.iam.api.dto.PermissionDelegationDtos.PermissionDelegationPage;
import com.lrj.oa.iam.api.dto.PermissionDelegationDtos.PermissionDelegationView;
import com.lrj.oa.iam.api.dto.PermissionDelegationDtos.RoleOption;
import com.lrj.oa.iam.application.PermissionDelegationService;
import com.lrj.oa.security.annotation.RequiresPerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 权限委托。与待办 {@code /iam/delegations} 分路径。 */
@RestController
@RequestMapping("/api/v1/iam/permission-delegations")
public class PermissionDelegationController {

    private final PermissionDelegationService delegations;

    public PermissionDelegationController(PermissionDelegationService delegations) {
        this.delegations = delegations;
    }

    @PostMapping
    @RequiresPerm("oa:iam:delegate")
    public Result<PermissionDelegationView> create(@RequestBody CreatePermissionDelegation body) {
        return Result.ok(delegations.create(body));
    }

    @GetMapping("/roles")
    @RequiresPerm("oa:iam:delegate")
    public Result<List<RoleOption>> roles() {
        return Result.ok(delegations.delegateableRoles());
    }

    @GetMapping
    @RequiresPerm("oa:iam:delegate")
    public Result<PermissionDelegationPage> list(@RequestParam(required = false) String cursor,
                                                 @RequestParam(defaultValue = "50") int size) {
        return Result.ok(delegations.listMine(parseCursor(cursor), size));
    }

    @PostMapping("/{id}/revoke")
    @RequiresPerm("oa:iam:delegate")
    public Result<PermissionDelegationView> revoke(@PathVariable long id) {
        return Result.ok(delegations.revoke(id));
    }

    private static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException ex) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "cursor 必须是数字序号");
        }
    }
}
