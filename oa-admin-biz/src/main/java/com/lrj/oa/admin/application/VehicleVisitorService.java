package com.lrj.oa.admin.application;

import com.lrj.oa.admin.api.dto.AdminDtos;
import com.lrj.oa.admin.infrastructure.mapper.AdminMappers;
import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.crypto.SensitiveCrypto;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import com.lrj.oa.security.annotation.DataScope;
import com.lrj.oa.security.port.DataScopeAccessChecker;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 车辆预定与访客登记。
 *
 * <p>车辆与会议室是<b>同一个问题</b>：同一资源不许时间重叠。所以用同一个手段
 * （PG 排他约束），而不是在应用层再写一遍冲突检测 —— 两份实现迟早会不一致。
 */
@Service
public class VehicleVisitorService {

    private static final String VEHICLE_OVERLAP = "ex_vehicle_no_overlap";

    private final AdminMappers.VehicleVisitorMapper mapper;
    private final SensitiveCrypto crypto;
    private final DataScopeAccessChecker dataScope;

    public VehicleVisitorService(AdminMappers.VehicleVisitorMapper mapper, SensitiveCrypto crypto,
                                 DataScopeAccessChecker dataScope) {
        this.mapper = mapper;
        this.crypto = crypto;
        this.dataScope = dataScope;
    }

    public List<Map<String, Object>> vehicles() {
        return mapper.activeVehicles(TenantContext.get());
    }

    @Transactional
    public long bookVehicle(AdminDtos.BookVehicle cmd) {
        UserContext ctx = UserContextHolder.require();
        dataScope.require("oa:vehicle:book", ctx.primaryOrgId(), ctx.primaryOrgPath(), ctx.userId());
        if (!cmd.endAt().isAfter(cmd.startAt())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "结束时间必须晚于开始时间");
        }
        try {
            Long id = mapper.insertVehicleBooking(TenantContext.get(), cmd.vehicleId(), ctx.userId(),
                    cmd.purpose(), cmd.startAt(), cmd.endAt(), ctx.primaryOrgId(), ctx.primaryOrgPath());
            return id == null ? 0L : id;
        } catch (DataIntegrityViolationException e) {
            if (rootMessage(e).contains(VEHICLE_OVERLAP)) {
                throw BusinessException.of(ResultCode.CONFLICT, "该车辆在此时间段已被预定");
            }
            throw e;
        }
    }

    /**
     * 邀请访客。
     *
     * <p>访客手机号是<b>外部个人信息</b>，用与员工同一套 {@link SensitiveCrypto}：
     * 密文列存原文、HMAC 列供等值检索。不能因为"访客不是员工"就降低标准 ——
     * 泄露一份访客名单同样是数据事故，而且访客本人根本没有渠道来追责。
     */
    @Transactional
    public long inviteVisitor(AdminDtos.InviteVisitor cmd) {
        UserContext ctx = UserContextHolder.require();
        dataScope.require("oa:visitor:invite", ctx.primaryOrgId(), ctx.primaryOrgPath(), ctx.userId());
        byte[] enc = cmd.phone() == null ? null : crypto.encrypt(cmd.phone());
        String hash = cmd.phone() == null ? null : crypto.hash(cmd.phone());
        Long id = mapper.insertVisitor(TenantContext.get(), cmd.name(), enc, hash, cmd.company(),
                ctx.userId(), cmd.visitAt(), ctx.primaryOrgId(), ctx.primaryOrgPath());
        return id == null ? 0L : id;
    }

    /** 访客到访签到。只有接待人本人或行政能操作。 */
    @Transactional
    @DataScope(permission = "oa:visitor:manage", table = "oa_admin.visitor", alias = "v", module = "admin")
    public void checkIn(long visitorId) {
        int n = mapper.checkIn(TenantContext.get(), visitorId);
        if (n == 0) throw BusinessException.of(ResultCode.CONFLICT, "访客不存在或状态不允许签到");
    }

    @Transactional
    @DataScope(permission = "oa:visitor:manage", table = "oa_admin.visitor", alias = "v", module = "admin")
    public void checkOut(long visitorId) {
        int n = mapper.checkOut(TenantContext.get(), visitorId);
        if (n == 0) throw BusinessException.of(ResultCode.CONFLICT, "访客未签到或已离开");
    }

    /** 我邀请的访客。手机号不返回 —— 列表页不需要，返回了就是多一处泄露面。 */
    @DataScope(permission = "oa:visitor:invite", table = "oa_admin.visitor", alias = "v", module = "admin")
    public List<AdminDtos.VisitorView> myVisitors(int limit) {
        UserContext ctx = UserContextHolder.require();
        return mapper.ownVisitors(TenantContext.get(), ctx.userId(), Math.min(Math.max(limit, 1), 200))
                .stream().map(r -> new AdminDtos.VisitorView(r.id, r.name, r.company, r.hostId,
                        r.visitAt, r.leaveAt, r.status)).toList();
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? "" : t.getMessage();
    }
}
