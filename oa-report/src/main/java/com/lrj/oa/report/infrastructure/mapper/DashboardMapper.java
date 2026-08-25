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

    @Select("""
            SELECT a.biz_type,
                   count(*) AS total,
                   count(*) FILTER (WHERE a.status='FINISHED') AS finished,
                   count(*) FILTER (WHERE a.status&lt;&gt;'FINISHED') AS running,
                   count(*) FILTER (WHERE a.outcome='REJECTED') AS rejected,
                   round(avg(EXTRACT(EPOCH FROM (a.finished_at-a.submitted_at))/3600.0)
                         FILTER (WHERE a.finished_at IS NOT NULL)::numeric, 2) AS avg_hours,
                   round(max(EXTRACT(EPOCH FROM (a.finished_at-a.submitted_at))/3600.0)
                         FILTER (WHERE a.finished_at IS NOT NULL)::numeric, 2) AS max_hours
              FROM oa_sys.v_approval_efficiency_scoped a
             GROUP BY a.biz_type ORDER BY total DESC
            """)
    List<Map<String, Object>> approvalEfficiency();

    @Select("""
            SELECT a.work_date, count(*) AS total,
                   count(*) FILTER (WHERE a.status='NORMAL') AS normal,
                   count(*) FILTER (WHERE a.status='LATE') AS late,
                   count(*) FILTER (WHERE a.status='ABSENT') AS absent,
                   count(*) FILTER (WHERE a.status='LEAVE') AS on_leave
              FROM oa_sys.v_attendance_summary_scoped a
             WHERE a.work_date &gt;= current_date - #{days}::int
             GROUP BY a.work_date ORDER BY a.work_date DESC
            """)
    List<Map<String, Object>> attendanceSummary(@Param("days") int days);

    @Select("""
            SELECT a.category, a.status, count(*) AS cnt, coalesce(sum(a.price),0) AS total_price
              FROM oa_sys.v_asset_summary_scoped a
             GROUP BY a.category, a.status ORDER BY a.category, a.status
            """)
    List<Map<String, Object>> assetSummary();
}
