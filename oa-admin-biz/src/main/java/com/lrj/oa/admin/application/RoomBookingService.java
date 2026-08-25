package com.lrj.oa.admin.application;

import com.lrj.oa.admin.api.dto.AdminDtos;
import com.lrj.oa.admin.infrastructure.mapper.AdminMappers;
import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.security.annotation.DataScope;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import com.lrj.oa.security.port.DataScopeAccessChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 会议室预定。
 *
 * <p><b>时间冲突由数据库的排他约束杜绝</b>（{@code EXCLUDE USING gist}），
 * 应用层<b>不</b>再实现一遍"先查冲突再插入"——那是典型的 check-then-act：
 * 两个并发请求都查到"没冲突"，然后都插进去。要堵住它得引入分布式锁，
 * 而锁的粒度、超时、重入、脑裂全是新的坑。
 *
 * <p>应用层唯一要做的，是把约束冲突这个<b>数据库异常</b>翻译成一句人话。
 * 这也是唯一正确的分工：数据库保证正确性，应用层负责可读性。
 */
@Service
public class RoomBookingService {

    private static final Logger log = LoggerFactory.getLogger(RoomBookingService.class);
    /** 排他约束的名字。翻译异常时靠它区分"时间冲突"与其它完整性错误。 */
    private static final String OVERLAP_CONSTRAINT = "ex_room_no_overlap";

    private final AdminMappers.RoomMapper mapper;
    private final DataScopeAccessChecker dataScope;

    public RoomBookingService(AdminMappers.RoomMapper mapper, DataScopeAccessChecker dataScope) {
        this.mapper = mapper;
        this.dataScope = dataScope;
    }

    public List<AdminDtos.RoomView> rooms() {
        return mapper.activeRooms(TenantContext.get()).stream().map(r -> new AdminDtos.RoomView(
                r.id, r.code, r.name, r.location, r.capacity, r.equipment, r.status)).toList();
    }

    @Transactional
    public long book(AdminDtos.BookRoom cmd) {
        UserContext ctx = UserContextHolder.require();
        dataScope.require("oa:room:book", ctx.primaryOrgId(), ctx.primaryOrgPath(), ctx.userId());
        if (!cmd.endAt().isAfter(cmd.startAt())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "结束时间必须晚于开始时间");
        }
        Integer capacity = mapper.activeCapacity(TenantContext.get(), cmd.roomId());
        if (capacity == null) throw BusinessException.of(ResultCode.NOT_FOUND, "会议室不存在或已停用");
        if (cmd.attendees() > capacity) {
            throw BusinessException.of(ResultCode.BAD_REQUEST,
                    "参会人数 " + cmd.attendees() + " 超过会议室容量 " + capacity);
        }
        try {
            Long id = mapper.insertBooking(TenantContext.get(), cmd.roomId(), ctx.userId(), ctx.username(),
                    cmd.subject(), cmd.attendees(), cmd.startAt(), cmd.endAt(),
                    ctx.primaryOrgId(), ctx.primaryOrgPath());
            return id == null ? 0L : id;
        } catch (DataIntegrityViolationException e) {
            // 排他约束命中。这是【预期内】的并发结果，不是系统错误——不打堆栈。
            if (rootMessage(e).contains(OVERLAP_CONSTRAINT)) {
                throw BusinessException.of(ResultCode.CONFLICT, "该时间段已被预定");
            }
            throw e;
        }
    }

    /** 区间用 {@code [)} 左闭右开：9:00-10:00 与 10:00-11:00 不算冲突，紧邻的两场会应当都能订上。 */
    @DataScope(permission = "oa:room:book", table = "oa_admin.room_booking", alias = "b", module = "admin")
    public List<AdminDtos.BookingView> bookingsOf(long roomId, OffsetDateTime from, OffsetDateTime to) {
        return mapper.bookings(TenantContext.get(), roomId, from, to).stream().map(r ->
                new AdminDtos.BookingView(r.id, r.roomId, r.roomName, r.bookerId, r.bookerName,
                        r.subject, r.startAt, r.endAt, r.status)).toList();
    }

    @Transactional
    public void cancel(long bookingId) {
        UserContext ctx = UserContextHolder.require();
        // 只能取消自己订的。WHERE 里带 booker_id 是防越权的最后一道。
        int n = mapper.cancelOwn(TenantContext.get(), bookingId, ctx.userId());
        if (n == 0) throw BusinessException.of(ResultCode.NOT_FOUND, "预定不存在、已取消，或不是你订的");
        // 取消后该时间段立刻释放：排他约束带了 WHERE (status = 'BOOKED')，
        // 所以改状态就等于让出时间段，不需要删行——轨迹要留着。
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? "" : t.getMessage();
    }
}
