package com.lrj.oa.org.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.crypto.SensitiveCrypto;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.org.api.event.EmployeeAssignmentChangedEvent;
import com.lrj.oa.org.application.command.OrgCommands;
import com.lrj.oa.org.domain.*;
import com.lrj.oa.org.infrastructure.mapper.*;
import com.lrj.oa.security.context.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 员工与任职关系的写侧。调岗、兼岗、离职都在这里，共同守住"拉链不断裂"。 */
@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    private final EmployeeMapper employeeMapper;
    private final AssignmentMapper assignmentMapper;
    private final ReportingLineMapper reportingLineMapper;
    private final OrgUnitMapper orgUnitMapper;
    private final SensitiveCrypto crypto;
    private final ApplicationEventPublisher events;

    public EmployeeService(EmployeeMapper employeeMapper, AssignmentMapper assignmentMapper,
                           ReportingLineMapper reportingLineMapper, OrgUnitMapper orgUnitMapper,
                           SensitiveCrypto crypto, ApplicationEventPublisher events) {
        this.employeeMapper = employeeMapper;
        this.assignmentMapper = assignmentMapper;
        this.reportingLineMapper = reportingLineMapper;
        this.orgUnitMapper = orgUnitMapper;
        this.crypto = crypto;
        this.events = events;
    }

    @Transactional
    public Long create(OrgCommands.CreateEmployee cmd) {
        if (orgUnitMapper.selectById(cmd.primaryOrgId()) == null) {
            throw BusinessException.of(ResultCode.ORG_NOT_FOUND, "主岗组织不存在: " + cmd.primaryOrgId());
        }

        Employee e = new Employee();
        e.setTenantId(TenantContext.get());
        e.setUserId(cmd.userId());
        e.setEmpNo(cmd.empNo());
        e.setName(cmd.name());
        e.setEnName(cmd.enName());
        e.setEmail(cmd.email());
        e.setMobileEnc(crypto.encrypt(cmd.mobile()));
        e.setMobileHash(crypto.hash(cmd.mobile()));
        e.setIdCardEnc(crypto.encrypt(cmd.idCard()));
        e.setIdCardHash(crypto.hash(cmd.idCard()));
        e.setGender(cmd.gender());
        e.setBirthday(cmd.birthday());
        e.setHireDate(cmd.hireDate() == null ? LocalDate.now() : cmd.hireDate());
        e.setEmploymentType(cmd.employmentType() == null ? "FULL_TIME" : cmd.employmentType());
        e.setStatus(EmployeeStatus.PROBATION.name());
        e.setVersion(0);
        e.setCreatedBy(currentUser());
        e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());

        try {
            employeeMapper.insert(e);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.of(ResultCode.CONFLICT, "工号或账号已存在: " + cmd.empNo() + " / " + cmd.userId());
        }

        openAssignment(e.getId(), cmd.primaryOrgId(), cmd.primaryPositionId(),
                AssignmentType.PRIMARY, false, LocalDate.now());

        if (cmd.managerEmployeeId() != null) {
            setReportingLine(e.getId(), new OrgCommands.SetReportingLine(
                    cmd.managerEmployeeId(), ReportingType.SOLID.name(), LocalDate.now()));
        }

        log.info("新建员工 id={} empNo={} userId={} 主岗org={}", e.getId(), e.getEmpNo(), e.getUserId(), cmd.primaryOrgId());
        return e.getId();
    }

    @Transactional
    public void update(Long employeeId, OrgCommands.UpdateEmployee cmd) {
        Employee e = requireEmployee(employeeId);
        if (cmd.name() != null) e.setName(cmd.name());
        if (cmd.enName() != null) e.setEnName(cmd.enName());
        if (cmd.email() != null) e.setEmail(cmd.email());
        if (cmd.avatar() != null) e.setAvatar(cmd.avatar());
        if (cmd.gender() != null) e.setGender(cmd.gender());
        if (cmd.birthday() != null) e.setBirthday(cmd.birthday());
        if (cmd.mobile() != null) {
            e.setMobileEnc(crypto.encrypt(cmd.mobile()));
            e.setMobileHash(crypto.hash(cmd.mobile()));
        }
        e.setUpdatedBy(currentUser());
        e.setUpdatedAt(OffsetDateTime.now());
        if (employeeMapper.updateById(e) == 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "员工信息已被他人修改，请刷新后重试");
        }
    }

    /**
     * 调岗。关闭旧主岗 + 开新主岗，<b>都不删行</b> ——
     * 两年后要能回答"这张单子发起时他在哪个部门"，靠的就是这条拉链没断。
     */
    @Transactional
    public void transfer(Long employeeId, OrgCommands.TransferEmployee cmd) {
        requireEmployee(employeeId);
        if (orgUnitMapper.selectById(cmd.targetOrgId()) == null) {
            throw BusinessException.of(ResultCode.ORG_NOT_FOUND, "目标组织不存在: " + cmd.targetOrgId());
        }
        LocalDate effective = cmd.effectiveDate() == null ? LocalDate.now() : cmd.effectiveDate();

        List<EmployeeOrgAssignment> active = assignmentMapper.selectActiveByEmployee(employeeId);
        active.stream()
                .filter(a -> a.typeEnum() == AssignmentType.PRIMARY)
                .forEach(a -> {
                    if (a.getOrgUnitId().equals(cmd.targetOrgId())) {
                        throw BusinessException.of(ResultCode.CONFLICT, "该员工已在目标组织任主岗");
                    }
                    assignmentMapper.close(a.getId(), effective);
                });

        openAssignment(employeeId, cmd.targetOrgId(), cmd.targetPositionId(),
                AssignmentType.PRIMARY, Boolean.TRUE.equals(cmd.asLeader()), effective);
        // 数据范围锚定在本人所在组织上，调岗后必须让权限快照跟着失效
        publishAssignmentChanged(employeeId, "transfer");
        log.info("员工 {} 调岗至组织 {} 生效日 {}（{}）", employeeId, cmd.targetOrgId(), effective, cmd.reason());
    }

    /** 加兼岗或虚线归属。主岗请走 {@link #transfer}。 */
    @Transactional
    public Long addAssignment(Long employeeId, OrgCommands.AddAssignment cmd) {
        requireEmployee(employeeId);
        AssignmentType type;
        try {
            type = AssignmentType.valueOf(cmd.assignmentType());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "非法任职类型: " + cmd.assignmentType());
        }
        if (type == AssignmentType.PRIMARY) {
            throw BusinessException.of(ResultCode.PRIMARY_ASSIGNMENT_CONFLICT,
                    "主岗只能通过调岗接口变更，以保证旧主岗被正确关闭");
        }
        if (orgUnitMapper.selectById(cmd.orgUnitId()) == null) {
            throw BusinessException.of(ResultCode.ORG_NOT_FOUND, "组织不存在: " + cmd.orgUnitId());
        }
        Long id = openAssignment(employeeId, cmd.orgUnitId(), cmd.positionId(), type,
                Boolean.TRUE.equals(cmd.asLeader()),
                cmd.validFrom() == null ? LocalDate.now() : cmd.validFrom());
        publishAssignmentChanged(employeeId, "addAssignment");
        return id;
    }

    @Transactional
    public void closeAssignment(Long assignmentId, LocalDate validTo) {
        EmployeeOrgAssignment a = assignmentMapper.selectById(assignmentId);
        if (a == null || !a.active()) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "任职关系不存在或已关闭: " + assignmentId);
        }
        if (a.typeEnum() == AssignmentType.PRIMARY) {
            throw BusinessException.of(ResultCode.CONFLICT, "不能直接关闭主岗，请走调岗或离职");
        }
        assignmentMapper.close(assignmentId, validTo == null ? LocalDate.now() : validTo);
        publishAssignmentChanged(a.getEmployeeId(), "closeAssignment");
    }

    @Transactional
    public void setReportingLine(Long employeeId, OrgCommands.SetReportingLine cmd) {
        requireEmployee(employeeId);
        if (employeeId.equals(cmd.managerEmployeeId())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "不能把自己设为自己的上级");
        }
        requireEmployee(cmd.managerEmployeeId());
        ReportingType type;
        try {
            type = ReportingType.valueOf(cmd.type());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "非法汇报线类型: " + cmd.type());
        }
        LocalDate from = cmd.validFrom() == null ? LocalDate.now() : cmd.validFrom();

        // 汇报环检查：沿着新上级往上走，若能走回自己就是环。深度有限（<64），直接走。
        Long cursor = cmd.managerEmployeeId();
        for (int i = 0; i < 64 && cursor != null; i++) {
            if (cursor.equals(employeeId)) {
                throw BusinessException.of(ResultCode.ORG_CYCLE, "该设置会造成汇报关系成环");
            }
            cursor = reportingLineMapper.selectSolidManagerId(cursor);
        }

        if (type == ReportingType.SOLID) {
            reportingLineMapper.closeActive(employeeId, ReportingType.SOLID.name(), from);
        }
        ReportingLine rl = new ReportingLine();
        rl.setTenantId(TenantContext.get());
        rl.setEmployeeId(employeeId);
        rl.setManagerEmployeeId(cmd.managerEmployeeId());
        rl.setType(type.name());
        rl.setValidFrom(from);
        rl.setCreatedAt(OffsetDateTime.now());
        reportingLineMapper.insert(rl);
    }

    /** 离职。关闭全部任职与汇报线，员工置 LEFT；行一律不删。 */
    @Transactional
    public void leave(Long employeeId, LocalDate leaveDate) {
        Employee e = requireEmployee(employeeId);
        LocalDate day = leaveDate == null ? LocalDate.now() : leaveDate;
        assignmentMapper.selectActiveByEmployee(employeeId)
                .forEach(a -> assignmentMapper.close(a.getId(), day));
        reportingLineMapper.closeActive(employeeId, ReportingType.SOLID.name(), day);
        reportingLineMapper.closeActive(employeeId, ReportingType.DOTTED.name(), day);
        e.setStatus(EmployeeStatus.LEFT.name());
        e.setLeaveDate(day);
        e.setUpdatedBy(currentUser());
        e.setUpdatedAt(OffsetDateTime.now());
        employeeMapper.updateById(e);
        // 离职是收权动作：连带撤销该账号的全部授权（oa-iam 侧监听处理）
        events.publishEvent(EmployeeAssignmentChangedEvent.left(e.getUserId()));
        log.info("员工 {} 离职，生效日 {}，已触发授权回收", employeeId, day);
    }

    // ───────────────────────────────────────────── 内部

    private Long openAssignment(Long employeeId, Long orgId, Long positionId,
                                AssignmentType type, boolean leader, LocalDate from) {
        EmployeeOrgAssignment a = new EmployeeOrgAssignment();
        a.setTenantId(TenantContext.get());
        a.setEmployeeId(employeeId);
        a.setOrgUnitId(orgId);
        a.setPositionId(positionId);
        a.setAssignmentType(type.name());
        a.setIsLeader(leader);
        a.setValidFrom(from);
        a.setCreatedBy(currentUser());
        a.setCreatedAt(OffsetDateTime.now());
        try {
            assignmentMapper.insert(a);
        } catch (DuplicateKeyException ex) {
            // 撞上 uk_primary_assignment 偏唯一索引 —— 数据库替我们挡住了双主岗
            throw BusinessException.of(ResultCode.PRIMARY_ASSIGNMENT_CONFLICT,
                    "该员工已有生效中的主岗，请先调岗关闭旧主岗");
        }
        return a.getId();
    }

    private void publishAssignmentChanged(Long employeeId, String reason) {
        Employee e = employeeMapper.selectById(employeeId);
        if (e != null) events.publishEvent(EmployeeAssignmentChangedEvent.of(e.getUserId(), reason));
    }

    private Employee requireEmployee(Long id) {
        Employee e = employeeMapper.selectById(id);
        if (e == null) throw BusinessException.of(ResultCode.EMPLOYEE_NOT_FOUND, "员工不存在: " + id);
        return e;
    }

    private static String currentUser() {
        var ctx = UserContextHolder.peek();
        return ctx == null ? "system" : ctx.userId();
    }
}
