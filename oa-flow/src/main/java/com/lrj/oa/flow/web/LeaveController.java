package com.lrj.oa.flow.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.flow.api.dto.LeaveBalanceView;
import com.lrj.oa.flow.api.dto.LeaveRequestView;
import com.lrj.oa.flow.application.LeaveService;
import com.lrj.oa.flow.application.command.FlowCommands;
import com.lrj.oa.flow.domain.LeaveType;
import com.lrj.oa.security.annotation.RequiresPerm;
import com.lrj.oa.security.context.UserContextHolder;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/flow/leave")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) { this.leaveService = leaveService; }

    @GetMapping("/types")
    @RequiresPerm("oa:leave:apply")
    public Result<List<LeaveType>> types() { return Result.ok(leaveService.activeTypes()); }

    @GetMapping("/balances")
    @RequiresPerm("oa:leave:apply")
    public Result<List<LeaveBalanceView>> myBalances() {
        return Result.ok(leaveService.myBalances(UserContextHolder.require().userId()));
    }

    @PostMapping
    @RequiresPerm("oa:leave:apply")
    public Result<LeaveRequestView> submit(@Valid @RequestBody FlowCommands.SubmitLeave cmd) {
        return Result.ok(leaveService.submit(cmd));
    }

    @GetMapping("/{requestNo}")
    @RequiresPerm("oa:leave:view")
    public Result<LeaveRequestView> get(@PathVariable String requestNo) {
        return Result.ok(leaveService.findByNo(requestNo));
    }

    /** 发放年度额度。生产由 HR 批量导入或 oa-job-service 年初跑批，这里保留手动入口。 */
    @PostMapping("/balances/grant")
    @RequiresPerm("oa:leave:grant")
    public Result<Void> grant(@Valid @RequestBody FlowCommands.GrantBalance cmd) {
        leaveService.grantBalance(cmd);
        return Result.ok();
    }
}
