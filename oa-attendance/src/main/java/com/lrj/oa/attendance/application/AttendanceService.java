package com.lrj.oa.attendance.application;

import com.lrj.oa.attendance.infrastructure.mapper.PunchMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 考勤日结。
 *
 * <p>聚合完全在数据库里完成 —— 万人级下把 500 万行捞到应用里算既慢又吃内存。
 * 生产由 oa-job-service 夜间按 {@code employee_id} 哈希分片跑；
 * 这里保留可手动触发的入口，便于验证与补算。
 */
@Service
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    private final PunchMapper punchMapper;

    public AttendanceService(PunchMapper punchMapper) { this.punchMapper = punchMapper; }

    public Map<String, Object> computeDaily(LocalDate date) {
        long t0 = System.nanoTime();
        int rows = punchMapper.computeDaily(date);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        log.info("考勤日结 {} 完成：{} 人，耗时 {}ms", date, rows, ms);

        Map<String, Object> stats = new HashMap<>();
        stats.put("date", date.toString());
        stats.put("employees", rows);
        stats.put("elapsedMs", ms);
        Map<String, Object> byStatus = new HashMap<>();
        for (Map<String, Object> r : punchMapper.dailyStats(date)) {
            byStatus.put(String.valueOf(r.get("status")), r.get("cnt"));
        }
        stats.put("byStatus", byStatus);
        return stats;
    }

    public List<Map<String, Object>> myDay(String userId, LocalDate date) {
        return punchMapper.selectDay(userId, date);
    }

    public Map<String, Object> health(LocalDate date) {
        return Map.of(
                "punchCount", punchMapper.countByDate(date),
                "duplicates", punchMapper.countDuplicates(date),
                "deadLetters", punchMapper.countDeadLetters());
    }
}
