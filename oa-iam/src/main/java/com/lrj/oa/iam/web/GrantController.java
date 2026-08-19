package com.lrj.oa.iam.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.iam.application.GrantService;
import com.lrj.oa.iam.application.command.IamCommands;
import com.lrj.oa.iam.domain.Delegation;
import com.lrj.oa.iam.domain.GrantRecord;
import com.lrj.oa.security.annotation.RequiresPerm;
import com.lrj.oa.security.context.UserContextHolder;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/iam")
public class GrantController {

    private final GrantService grantService;

    public GrantController(GrantService grantService) { this.grantService = grantService; }

    @GetMapping("/grants")
    @RequiresPerm("oa:iam:view")
    public Result<List<GrantRecord>> list(@RequestParam String subjectType, @RequestParam String subjectId) {
        return Result.ok(grantService.listBySubject(subjectType, subjectId));
    }

    @PostMapping("/grants")
    @RequiresPerm("oa:iam:grant")
    public Result<Long> grant(@Valid @RequestBody IamCommands.Grant cmd) {
        return Result.ok(grantService.grant(cmd));
    }

    @DeleteMapping("/grants/{grantId}")
    @RequiresPerm("oa:iam:revoke")
    public Result<Void> revoke(@PathVariable Long grantId,
                               @RequestParam(required = false) String reason) {
        grantService.revoke(grantId, reason);
        return Result.ok();
    }

    /**
     * 申请 JIT 临时提权。只能给自己申请（替别人提权是授权行为，走 /grants），
     * 且只能激活自己已持有的角色 —— 真正的闸门在 {@code GrantService.elevate} 里。
     */
    @PostMapping("/elevations")
    @RequiresPerm("oa:iam:elevate")
    public Result<Long> elevate(@Valid @RequestBody IamCommands.Elevate cmd) {
        String me = UserContextHolder.require().userId();
        return Result.ok(grantService.elevate(me, cmd));
    }

    @GetMapping("/delegations")
    @RequiresPerm("oa:iam:delegate")
    public Result<List<Delegation>> myDelegations() {
        return Result.ok(grantService.listDelegationsOf(UserContextHolder.require().userId()));
    }

    @PostMapping("/delegations")
    @RequiresPerm("oa:iam:delegate")
    public Result<Long> delegate(@Valid @RequestBody IamCommands.Delegate cmd) {
        String me = UserContextHolder.require().userId();
        return Result.ok(grantService.delegate(me, cmd));
    }

    @DeleteMapping("/delegations/{id}")
    @RequiresPerm("oa:iam:delegate")
    public Result<Void> revokeDelegation(@PathVariable Long id) {
        var mine = grantService.listDelegationsOf(UserContextHolder.require().userId());
        if (mine.stream().noneMatch(d -> d.getId().equals(id))) {
            // 只能撤销自己发出的委托，否则就是越权操作别人的委托关系
            throw BusinessException.of(ResultCode.PERM_DENIED, "只能撤销自己发出的委托");
        }
        grantService.revokeDelegation(id);
        return Result.ok();
    }
}
