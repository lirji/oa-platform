package com.lrj.oa.org.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.org.api.OrgQueryApi;
import com.lrj.oa.org.api.dto.AssignmentView;
import com.lrj.oa.org.api.dto.EmployeeView;
import com.lrj.oa.org.api.dto.OrgSnapshotView;
import com.lrj.oa.org.application.EmployeeService;
import com.lrj.oa.org.application.command.OrgCommands;
import com.lrj.oa.security.annotation.RequiresPerm;
import com.lrj.oa.security.port.DataScopeAccessChecker;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/org/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final OrgQueryApi orgQuery;
    private final DataScopeAccessChecker dataScope;

    public EmployeeController(EmployeeService employeeService, OrgQueryApi orgQuery,
                              DataScopeAccessChecker dataScope) {
        this.employeeService = employeeService;
        this.orgQuery = orgQuery;
        this.dataScope = dataScope;
    }

    @GetMapping("/by-user/{userId}")
    @RequiresPerm("oa:employee:view")
    public Result<EmployeeView> byUser(@PathVariable String userId) {
        EmployeeView employee = requireView(userId, "oa:employee:view");
        return Result.ok(employee);
    }

    @GetMapping("/by-user/{userId}/assignments")
    @RequiresPerm("oa:employee:view")
    public Result<List<AssignmentView>> assignments(@PathVariable String userId) {
        requireView(userId, "oa:employee:view");
        return Result.ok(orgQuery.activeAssignments(userId));
    }

    /**
     * 历史时点的组织归属。这是"两年前那张单子他当时在哪个部门"的答案来源。
     */
    @GetMapping("/by-user/{userId}/as-of")
    @RequiresPerm("oa:employee:view")
    public Result<OrgSnapshotView> asOf(@PathVariable String userId,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        OrgSnapshotView snapshot = orgQuery.asOf(userId, date);
        dataScope.require("oa:employee:view", snapshot.primaryOrgId(), snapshot.primaryOrgPath(), userId);
        return Result.ok(snapshot);
    }

    @GetMapping("/by-user/{userId}/manager-chain")
    @RequiresPerm("oa:employee:view")
    public Result<List<String>> managerChain(@PathVariable String userId,
                                             @RequestParam(defaultValue = "5") int maxLevel) {
        requireView(userId, "oa:employee:view");
        return Result.ok(orgQuery.managerChain(userId, maxLevel));
    }

    @PostMapping
    @RequiresPerm("oa:employee:create")
    public Result<Long> create(@Valid @RequestBody OrgCommands.CreateEmployee cmd) {
        return Result.ok(employeeService.create(cmd));
    }

    @PutMapping("/{employeeId}")
    @RequiresPerm("oa:employee:update")
    public Result<Void> update(@PathVariable Long employeeId, @RequestBody OrgCommands.UpdateEmployee cmd) {
        employeeService.update(employeeId, cmd);
        return Result.ok();
    }

    @PostMapping("/{employeeId}/transfer")
    @RequiresPerm("oa:employee:transfer")
    public Result<Void> transfer(@PathVariable Long employeeId,
                                 @Valid @RequestBody OrgCommands.TransferEmployee cmd) {
        employeeService.transfer(employeeId, cmd);
        return Result.ok();
    }

    @PostMapping("/{employeeId}/assignments")
    @RequiresPerm("oa:employee:transfer")
    public Result<Long> addAssignment(@PathVariable Long employeeId,
                                      @Valid @RequestBody OrgCommands.AddAssignment cmd) {
        return Result.ok(employeeService.addAssignment(employeeId, cmd));
    }

    @DeleteMapping("/assignments/{assignmentId}")
    @RequiresPerm("oa:employee:transfer")
    public Result<Void> closeAssignment(@PathVariable Long assignmentId,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validTo) {
        employeeService.closeAssignment(assignmentId, validTo);
        return Result.ok();
    }

    @PutMapping("/{employeeId}/reporting-line")
    @RequiresPerm("oa:employee:transfer")
    public Result<Void> setReportingLine(@PathVariable Long employeeId,
                                         @Valid @RequestBody OrgCommands.SetReportingLine cmd) {
        employeeService.setReportingLine(employeeId, cmd);
        return Result.ok();
    }

    @PostMapping("/{employeeId}/leave")
    @RequiresPerm("oa:employee:leave")
    public Result<Void> leave(@PathVariable Long employeeId,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate leaveDate) {
        employeeService.leave(employeeId, leaveDate);
        return Result.ok();
    }

    private EmployeeView requireView(String userId, String permission) {
        EmployeeView employee = orgQuery.getEmployeeByUserId(userId);
        dataScope.require(permission, employee.primaryOrgId(), employee.primaryOrgPath(), employee.userId());
        return employee;
    }
}
