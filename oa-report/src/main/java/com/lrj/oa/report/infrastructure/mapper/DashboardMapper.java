package com.lrj.oa.report.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {

    @Select("""
            <script>
            SELECT h.org_id, h.org_name, h.org_path, h.headcount
              FROM oa_sys.v_headcount h
             WHERE 1=1
             <if test="requestedPrefix != null and requestedPrefix != ''">
               AND h.org_path LIKE concat(#{requestedPrefix}, '%')
             </if>
             ORDER BY h.headcount DESC LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> headcount(@Param("requestedPrefix") String requestedPrefix,
                                        @Param("limit") int limit);

    /**
     * 审批时效。不用 {@code FILTER}/{@code ::}：数据权限拦截器要先用 JSQLParser 解析整句，
     * 那两处 PostgreSQL 语法会直接让接口 500。
     */

    @Select("""
            SELECT a.biz_type,
                   count(*) AS total,
                   sum(CASE WHEN a.status = 'FINISHED' THEN 1 ELSE 0 END) AS finished,
                   sum(CASE WHEN a.status <> 'FINISHED' THEN 1 ELSE 0 END) AS running,
                   sum(CASE WHEN a.outcome = 'REJECTED' THEN 1 ELSE 0 END) AS rejected,
                   round(avg(CASE WHEN a.finished_at IS NOT NULL
                             THEN extract(epoch FROM (a.finished_at - a.submitted_at)) / 3600.0
                             END), 2) AS avg_hours,
                   round(max(CASE WHEN a.finished_at IS NOT NULL
                             THEN extract(epoch FROM (a.finished_at - a.submitted_at)) / 3600.0
                             END), 2) AS max_hours
              FROM oa_sys.v_approval_efficiency_scoped a
             GROUP BY a.biz_type
             ORDER BY total DESC
            """)
    List<Map<String, Object>> approvalEfficiency();

    @Select("""
            SELECT a.work_date, count(*) AS total,
                   sum(CASE WHEN a.status = 'NORMAL' THEN 1 ELSE 0 END) AS n_normal,
                   sum(CASE WHEN a.status = 'LATE' THEN 1 ELSE 0 END) AS n_late,
                   sum(CASE WHEN a.status = 'ABSENT' THEN 1 ELSE 0 END) AS n_absent,
                   sum(CASE WHEN a.status = 'LEAVE' THEN 1 ELSE 0 END) AS n_leave
              FROM oa_sys.v_attendance_summary_scoped a
             WHERE a.work_date >= current_date - #{days}
             GROUP BY a.work_date
             ORDER BY a.work_date DESC
            """)
    List<Map<String, Object>> attendanceSummary(@Param("days") int days);

    @Select("""
            SELECT a.category, a.status, count(*) AS cnt, coalesce(sum(a.price),0) AS total_price
              FROM oa_sys.v_asset_summary_scoped a
             GROUP BY a.category, a.status ORDER BY a.category, a.status
            """)
    List<Map<String, Object>> assetSummary();
}
