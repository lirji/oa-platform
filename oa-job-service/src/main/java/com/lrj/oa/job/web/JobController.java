package com.lrj.oa.job.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.job.application.OaJobs;
import com.lrj.oa.security.annotation.PublicApi;
import com.lrj.oa.security.annotation.RequiresPerm;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 跑批的手动触发与查询 :8403。
 *
 * <p>定时器是主路径，这些接口是<b>补救路径</b>：某天的日结失败了、某个月的分区忘了建，
 * 需要有办法重跑而不必改代码或改 cron。所有触发都走同一套幂等，重复调用安全。
 */
@RestController
@RequestMapping("/api/v1/job")
public class JobController {

    private final OaJobs jobs;
    private final JdbcTemplate jdbc;

    public JobController(OaJobs jobs, JdbcTemplate jdbc) {
        this.jobs = jobs;
        this.jdbc = jdbc;
    }

    @PostMapping("/attendance-daily")
    @RequiresPerm("oa:job:run")
    public Result<Map<String, Object>> attendanceDaily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        LocalDate d = day == null ? LocalDate.now().minusDays(1) : day;
        return Result.ok(Map.of("date", d.toString(), "rows", jobs.computeAttendanceFor(d)));
    }

    @PostMapping("/grant-expire")
    @RequiresPerm("oa:job:run")
    public Result<Void> expireGrants() { jobs.expireGrants(); return Result.ok(); }

    @PostMapping("/partition-roll")
    @RequiresPerm("oa:job:run")
    public Result<Void> rollPartitions() { jobs.rollPartitions(); return Result.ok(); }

    @PostMapping("/leave-annual-grant")
    @RequiresPerm("oa:job:run")
    public Result<Map<String, Object>> grantLeave(@RequestParam String period) {
        return Result.ok(Map.of("granted", jobs.grantAnnualLeaveFor(period)));
    }

    @GetMapping("/runs")
    @RequiresPerm("oa:job:run")
    public Result<List<Map<String, Object>>> runs(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(jdbc.queryForList("""
                SELECT job_name, shard, shard_total, idem_key, status, affected, error,
                       started_at, finished_at
                  FROM oa_sys.job_run ORDER BY id DESC LIMIT ?
                """, Math.min(Math.max(limit, 1), 200)));
    }

    @GetMapping("/ping")
    @PublicApi(reason = "存活探针，不返回业务数据；容器 healthcheck 与冒烟用")
    public Result<Map<String, Object>> ping() { return Result.ok(jobs.stats()); }
}
