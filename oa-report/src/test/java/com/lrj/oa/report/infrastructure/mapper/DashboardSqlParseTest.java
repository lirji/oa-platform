package com.lrj.oa.report.infrastructure.mapper;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 驾驶舱查询会被数据权限拦截器整句解析。FILTER / ::numeric 会让 JSQLParser 直接抛，接口 500。
 */
class DashboardSqlParseTest {

    @Test
    void approval_sql_is_jsqlparser_safe() {
        String sql = """
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
                """;
        assertThatCode(() -> CCJSqlParserUtil.parse(sql)).doesNotThrowAnyException();
    }

    @Test
    void attendance_sql_is_jsqlparser_safe() {
        String sql = """
                SELECT a.work_date, count(*) AS total,
                       sum(CASE WHEN a.status = 'NORMAL' THEN 1 ELSE 0 END) AS n_normal,
                       sum(CASE WHEN a.status = 'LATE' THEN 1 ELSE 0 END) AS n_late,
                       sum(CASE WHEN a.status = 'ABSENT' THEN 1 ELSE 0 END) AS n_absent,
                       sum(CASE WHEN a.status = 'LEAVE' THEN 1 ELSE 0 END) AS n_leave
                  FROM oa_sys.v_attendance_summary_scoped a
                 WHERE a.work_date >= current_date - ?
                 GROUP BY a.work_date
                 ORDER BY a.work_date DESC
                """;
        assertThatCode(() -> CCJSqlParserUtil.parse(sql)).doesNotThrowAnyException();
    }
}
