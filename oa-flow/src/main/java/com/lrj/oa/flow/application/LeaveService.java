package com.lrj.oa.flow.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.id.SegmentIdGenerator;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.flow.api.dto.LeaveBalanceView;
import com.lrj.oa.flow.api.dto.LeaveRequestView;
import com.lrj.oa.flow.application.command.FlowCommands;
import com.lrj.oa.flow.domain.LeaveBalance;
import com.lrj.oa.flow.domain.LeaveRequest;
import com.lrj.oa.flow.domain.LeaveType;
import com.lrj.oa.flow.infrastructure.mapper.FlowMappers;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 请假单 —— Phase 4 的端到端闭环。
 *
 * <p>额度处理分<b>冻结</b>与<b>消耗</b>两步：提单时先冻结，避免同一个人连提两单把额度用超；
 * 审批通过才真正消耗，驳回则释放。每一步都写一条流水，
 * 流水表上 {@code (request_id, action)} 唯一 —— <b>幂等由数据库保证</b>，
 * 重放同一条消息不会扣两次。
 */
@Service
public class LeaveService {

    private static final Logger log = LoggerFactory.getLogger(LeaveService.class);
    private static final String BIZ_TYPE = "LEAVE";
    private static final String PROCESS_KEY = "oaGenericApproval";

    private final FlowMappers.LeaveRequestMapper requestMapper;
    private final FlowMappers.LeaveTypeMapper typeMapper;
    private final FlowMappers.LeaveBalanceMapper balanceMapper;
    private final ApprovalService approvalService;
    private final ApproverResolver approverResolver;
    private final SegmentIdGenerator idGenerator;

    public LeaveService(FlowMappers.LeaveRequestMapper requestMapper, FlowMappers.LeaveTypeMapper typeMapper,
                        FlowMappers.LeaveBalanceMapper balanceMapper, ApprovalService approvalService,
                        ApproverResolver approverResolver, SegmentIdGenerator idGenerator) {
        this.requestMapper = requestMapper;
        this.typeMapper = typeMapper;
        this.balanceMapper = balanceMapper;
        this.approvalService = approvalService;
        this.approverResolver = approverResolver;
        this.idGenerator = idGenerator;
    }

    @PostConstruct
    void registerCallback() {
        approvalService.registerFinishHandler(BIZ_TYPE, this::onApprovalFinished);
    }

    // ───────────────────────────────────────────── 提单

    @Transactional
    public LeaveRequestView submit(FlowCommands.SubmitLeave cmd) {
        UserContext ctx = UserContextHolder.require();
        LeaveType type = typeMapper.selectByCode(cmd.leaveTypeCode());
        if (type == null) throw BusinessException.of(ResultCode.BAD_REQUEST, "假期类型不存在: " + cmd.leaveTypeCode());
        if (cmd.endDate().isBefore(cmd.startDate())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        if (cmd.days().signum() <= 0) throw BusinessException.of(ResultCode.BAD_REQUEST, "请假天数必须大于 0");
        if (type.getMaxDays() != null && cmd.days().compareTo(type.getMaxDays()) > 0) {
            throw BusinessException.of(ResultCode.BAD_REQUEST,
                    type.getName() + "单次不得超过 " + type.getMaxDays() + " 天");
        }

        // ★ 审批人在 OA 侧算好（ADR-0010），级数由天数决定
        int levels = approverResolver.levelsForLeave(cmd.days());
        List<String> chain = approverResolver.resolveChain(ctx.userId(), levels);

        LeaveRequest req = new LeaveRequest();
        req.setTenantId(TenantContext.get());
        req.setRequestNo(idGenerator.next("LEAVE"));
        req.setUserId(ctx.userId());
        req.setEmployeeId(ctx.employeeId());
        req.setLeaveTypeId(type.getId());
        req.setStartDate(cmd.startDate());
        req.setEndDate(cmd.endDate());
        req.setDays(cmd.days());
        req.setReason(cmd.reason());
        req.setStatus("PENDING");
        req.setOrgId(ctx.primaryOrgId());
        req.setOrgPath(ctx.primaryOrgPath());
        req.setCreatedAt(OffsetDateTime.now());
        req.setVersion(0);
        requestMapper.insert(req);

        // 先冻结额度：不冻结的话，连提两单都能通过校验，最后一起扣就超了
        if (Boolean.TRUE.equals(type.getNeedBalance())) {
            freeze(ctx.userId(), type, cmd.days(), req.getRequestNo());
        }

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("leaveTypeCode", type.getCode());
        formData.put("startDate", cmd.startDate().toString());
        formData.put("endDate", cmd.endDate().toString());
        formData.put("days", cmd.days());
        formData.put("reason", cmd.reason());

        approvalService.submit(new ApprovalService.SubmitRequest(
                BIZ_TYPE, req.getRequestNo(), PROCESS_KEY, "LEAVE", formData,
                ctx.username() + " 的" + type.getName() + "申请",
                cmd.startDate() + " ~ " + cmd.endDate() + "，共 " + cmd.days() + " 天",
                chain));

        log.info("请假单 {} 已提交：{} {} 天，审批链 {}", req.getRequestNo(), type.getName(), cmd.days(), chain);
        return toView(req, type, chain);
    }

    // ───────────────────────────────────────────── 审批结束回调

    @Transactional
    public void onApprovalFinished(String requestNo, String outcome) {
        LeaveRequest req = requestMapper.selectByNo(requestNo);
        if (req == null) { log.warn("审批结束但找不到请假单 {}", requestNo); return; }
        LeaveType type = typeMapper.selectById(req.getLeaveTypeId());

        if ("APPROVED".equals(outcome)) {
            req.setStatus("APPROVED");
            if (Boolean.TRUE.equals(type.getNeedBalance())) consume(req, type);
        } else {
            req.setStatus("REJECTED");
            if (Boolean.TRUE.equals(type.getNeedBalance())) release(req, type);
        }
        req.setDecidedAt(OffsetDateTime.now());
        requestMapper.updateById(req);
        log.info("请假单 {} 审批结束：{}", requestNo, outcome);
    }

    // ───────────────────────────────────────────── 额度

    private void freeze(String userId, LeaveType type, BigDecimal days, String requestNo) {
        LeaveBalance bal = requireBalance(userId, type);
        if (bal.available().compareTo(days) < 0) {
            throw BusinessException.of(ResultCode.LEAVE_BALANCE_INSUFFICIENT,
                    "%s 可用额度 %s 天，不足以请 %s 天".formatted(type.getName(), bal.available(), days));
        }
        if (balanceMapper.recordTxn(bal.getId(), requestNo, "FREEZE", days, null) == 0) {
            log.debug("请假单 {} 的冻结流水已存在，跳过（幂等）", requestNo);
            return;
        }
        bal.setFrozenDays(bal.getFrozenDays().add(days));
        applyOptimistic(bal, "冻结");
    }

    private void consume(LeaveRequest req, LeaveType type) {
        LeaveBalance bal = requireBalance(req.getUserId(), type);
        if (balanceMapper.recordTxn(bal.getId(), req.getRequestNo(), "CONSUME", req.getDays(), null) == 0) return;
        bal.setFrozenDays(bal.getFrozenDays().subtract(req.getDays()).max(BigDecimal.ZERO));
        bal.setUsedDays(bal.getUsedDays().add(req.getDays()));
        applyOptimistic(bal, "消耗");
    }

    private void release(LeaveRequest req, LeaveType type) {
        LeaveBalance bal = requireBalance(req.getUserId(), type);
        if (balanceMapper.recordTxn(bal.getId(), req.getRequestNo(), "RELEASE", req.getDays(), null) == 0) return;
        bal.setFrozenDays(bal.getFrozenDays().subtract(req.getDays()).max(BigDecimal.ZERO));
        applyOptimistic(bal, "释放");
    }

    private void applyOptimistic(LeaveBalance bal, String what) {
        bal.setUpdatedAt(OffsetDateTime.now());
        if (balanceMapper.updateById(bal) == 0) {
            // 同一个人的额度天然低并发，撞上乐观锁说明确实有并发操作，让调用方重试即可
            throw BusinessException.of(ResultCode.CONFLICT, "额度" + what + "时发生并发修改，请重试");
        }
    }

    private LeaveBalance requireBalance(String userId, LeaveType type) {
        String period = String.valueOf(LocalDate.now().getYear());
        LeaveBalance bal = balanceMapper.select(TenantContext.get(), userId, type.getId(), period);
        if (bal == null) {
            throw BusinessException.of(ResultCode.LEAVE_BALANCE_INSUFFICIENT,
                    "%s 尚未发放 %s 年度额度".formatted(type.getName(), period));
        }
        return bal;
    }

    @Transactional
    public void grantBalance(FlowCommands.GrantBalance cmd) {
        LeaveType type = typeMapper.selectByCode(cmd.leaveTypeCode());
        if (type == null) throw BusinessException.of(ResultCode.BAD_REQUEST, "假期类型不存在");
        balanceMapper.grantIfAbsent(TenantContext.get(), cmd.userId(), type.getId(), cmd.period(), cmd.days());
        LeaveBalance bal = balanceMapper.select(TenantContext.get(), cmd.userId(), type.getId(), cmd.period());
        if (bal.getTotalDays().compareTo(cmd.days()) != 0) {
            bal.setTotalDays(cmd.days());
            applyOptimistic(bal, "发放");
        }
    }

    public List<LeaveBalanceView> myBalances(String userId) {
        String period = String.valueOf(LocalDate.now().getYear());
        List<LeaveBalanceView> out = new ArrayList<>();
        for (LeaveType t : typeMapper.selectActive()) {
            if (!Boolean.TRUE.equals(t.getNeedBalance())) continue;
            LeaveBalance b = balanceMapper.select(TenantContext.get(), userId, t.getId(), period);
            LeaveBalanceView v = new LeaveBalanceView();
            v.setLeaveTypeCode(t.getCode());
            v.setLeaveTypeName(t.getName());
            v.setPeriod(period);
            v.setTotalDays(b == null ? BigDecimal.ZERO : b.getTotalDays());
            v.setUsedDays(b == null ? BigDecimal.ZERO : b.getUsedDays());
            v.setFrozenDays(b == null ? BigDecimal.ZERO : b.getFrozenDays());
            v.setAvailableDays(b == null ? BigDecimal.ZERO : b.available());
            out.add(v);
        }
        return out;
    }

    /** 可用的假期类型。经服务层暴露，Controller 不直连 Mapper（ArchUnit 会拦）。 */
    public List<LeaveType> activeTypes() { return typeMapper.selectActive(); }

    public LeaveRequestView findByNo(String requestNo) {
        LeaveRequest req = requestMapper.selectByNo(requestNo);
        if (req == null) throw BusinessException.of(ResultCode.NOT_FOUND, "请假单不存在: " + requestNo);
        return toView(req, typeMapper.selectById(req.getLeaveTypeId()), null);
    }

    private LeaveRequestView toView(LeaveRequest req, LeaveType type, List<String> chain) {
        LeaveRequestView v = new LeaveRequestView();
        v.setId(req.getId());
        v.setRequestNo(req.getRequestNo());
        v.setUserId(req.getUserId());
        v.setLeaveTypeCode(type == null ? null : type.getCode());
        v.setLeaveTypeName(type == null ? null : type.getName());
        v.setStartDate(req.getStartDate());
        v.setEndDate(req.getEndDate());
        v.setDays(req.getDays());
        v.setReason(req.getReason());
        v.setStatus(req.getStatus());
        v.setOrgId(req.getOrgId());
        v.setOrgPath(req.getOrgPath());
        v.setCreatedAt(req.getCreatedAt());
        v.setApproverChain(chain);
        var ins = approvalService.findByBusiness(BIZ_TYPE, req.getRequestNo());
        if (ins != null) v.setProcessInstanceId(ins.getProcessInstanceId());
        return v;
    }
}
