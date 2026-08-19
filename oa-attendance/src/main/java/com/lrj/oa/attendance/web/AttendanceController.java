package com.lrj.oa.attendance.web;

import com.lrj.oa.attendance.application.AttendanceService;
import com.lrj.oa.attendance.application.PunchService;
import com.lrj.oa.common.api.Result;
import com.lrj.oa.security.annotation.RequiresPerm;
import com.lrj.oa.security.context.UserContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    private final PunchService punchService;
    private final AttendanceService attendanceService;

    public AttendanceController(PunchService punchService, AttendanceService attendanceService) {
        this.punchService = punchService;
        this.attendanceService = attendanceService;
    }

    /** 打卡。入队即返回，落库在后台批量完成 —— 早高峰扛得住的关键。 */
    @PostMapping("/punch")
    @RequiresPerm("oa:attendance:punch")
    public Result<PunchService.PunchResult> punch(@RequestParam(defaultValue = "IN") String type,
                                                  @RequestParam(required = false) BigDecimal lat,
                                                  @RequestParam(required = false) BigDecimal lng,
                                                  @RequestParam(required = false) String deviceId,
                                                  @RequestParam(required = false) String source) {
        return Result.ok(punchService.punch(type, lat, lng, deviceId, source));
    }

    @GetMapping("/me")
    @RequiresPerm("oa:attendance:view")
    public Result<List<Map<String, Object>>> myDay(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(attendanceService.myDay(UserContextHolder.require().userId(),
                date == null ? LocalDate.now() : date));
    }

    @PostMapping("/daily-compute")
    @RequiresPerm("oa:attendance:admin")
    public Result<Map<String, Object>> computeDaily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(attendanceService.computeDaily(date == null ? LocalDate.now() : date));
    }

    /** 削峰链路的健康度：入队/落库/降级/死信/队列深度。 */
    @GetMapping("/admin/stats")
    @RequiresPerm("oa:attendance:admin")
    public Result<Map<String, Object>> stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        punchService.flushNow();
        return Result.ok(Map.of(
                "pipeline", punchService.stats(),
                "storage", attendanceService.health(date == null ? LocalDate.now() : date)));
    }
}
