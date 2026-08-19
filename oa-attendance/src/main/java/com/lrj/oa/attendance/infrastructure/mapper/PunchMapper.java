package com.lrj.oa.attendance.infrastructure.mapper;

import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface PunchMapper {

    /**
     * 批量落库。
     *
     * <p>{@code ON CONFLICT DO NOTHING} 是第二道幂等闸门 —— 第一道在 Redis。
     * Redis 挂了或键被清空时，数据库这道仍然守得住"同一个人同一天同一类型只有一条"。
     * 幂等只靠缓存是不牢的，缓存本来就是可以丢的东西。
     */
    @Insert("""
            <script>
            INSERT INTO oa_att.punch_record
                (tenant_id, user_id, employee_id, punch_date, punch_type, punch_time, source,
                 latitude, longitude, device_id, org_id, org_path)
            VALUES
            <foreach item="p" collection="list" separator=",">
                (#{p.tenantId}, #{p.userId}, #{p.employeeId}, #{p.punchDate}, #{p.punchType},
                 #{p.punchTime}, #{p.source}, #{p.latitude}, #{p.longitude}, #{p.deviceId},
                 #{p.orgId}, #{p.orgPath})
            </foreach>
            ON CONFLICT DO NOTHING
            </script>
            """)
    int batchInsert(@Param("list") List<PunchRow> list);

    @Select("""
            SELECT punch_type AS "punchType", punch_time AS "punchTime"
              FROM oa_att.punch_record
             WHERE user_id = #{userId} AND punch_date = #{date}
             ORDER BY punch_time
            """)
    List<Map<String, Object>> selectDay(@Param("userId") String userId, @Param("date") LocalDate date);

    @Select("SELECT count(*) FROM oa_att.punch_record WHERE punch_date = #{date}")
    long countByDate(@Param("date") LocalDate date);

    @Select("""
            SELECT count(*) FROM (
              SELECT user_id, punch_date, punch_type, count(*) AS c
                FROM oa_att.punch_record WHERE punch_date = #{date}
               GROUP BY 1,2,3 HAVING count(*) > 1
            ) dup
            """)
    long countDuplicates(@Param("date") LocalDate date);

    @Insert("INSERT INTO oa_att.punch_dead_letter(payload, error) VALUES (#{payload}, #{error})")
    int deadLetter(@Param("payload") String payload, @Param("error") String error);

    @Select("SELECT count(*) FROM oa_att.punch_dead_letter")
    long countDeadLetters();

    /**
     * 日结聚合：把一天的打卡压成每人一行。
     *
     * <p>整个聚合在数据库里一次完成，而不是把 500 万行捞到应用里算 ——
     * 万人级下后者既慢又吃内存。
     */
    @Insert("""
            INSERT INTO oa_att.attendance_daily
                (tenant_id, user_id, work_date, first_in, last_out, work_minutes, status, org_id, org_path, computed_at)
            SELECT p.tenant_id, p.user_id, p.punch_date,
                   min(p.punch_time) FILTER (WHERE p.punch_type = 'IN')  AS first_in,
                   max(p.punch_time) FILTER (WHERE p.punch_type = 'OUT') AS last_out,
                   coalesce(EXTRACT(EPOCH FROM (
                       max(p.punch_time) FILTER (WHERE p.punch_type = 'OUT') -
                       min(p.punch_time) FILTER (WHERE p.punch_type = 'IN')))::int / 60, 0) AS work_minutes,
                   CASE
                     WHEN count(*) FILTER (WHERE p.punch_type = 'IN')  = 0 THEN 'ABSENT'
                     WHEN count(*) FILTER (WHERE p.punch_type = 'OUT') = 0 THEN 'MISSING'
                     WHEN (min(p.punch_time) FILTER (WHERE p.punch_type = 'IN'))::time
                          > (SELECT start_time FROM oa_att.shift WHERE code = 'STANDARD') THEN 'LATE'
                     ELSE 'NORMAL'
                   END AS status,
                   max(p.org_id), max(p.org_path), now()
              FROM oa_att.punch_record p
             WHERE p.punch_date = #{date}
             GROUP BY p.tenant_id, p.user_id, p.punch_date
            ON CONFLICT (tenant_id, user_id, work_date) DO UPDATE SET
                first_in = excluded.first_in, last_out = excluded.last_out,
                work_minutes = excluded.work_minutes, status = excluded.status,
                computed_at = excluded.computed_at
            """)
    int computeDaily(@Param("date") LocalDate date);

    @Select("""
            SELECT status, count(*) AS cnt FROM oa_att.attendance_daily
             WHERE work_date = #{date} GROUP BY status
            """)
    List<Map<String, Object>> dailyStats(@Param("date") LocalDate date);

    /** 批量插入的行结构。字段名与 SQL 里的 #{p.xxx} 对应。 */
    class PunchRow {
        public Long tenantId; public String userId; public Long employeeId;
        public LocalDate punchDate; public String punchType;
        public java.time.OffsetDateTime punchTime; public String source;
        public java.math.BigDecimal latitude; public java.math.BigDecimal longitude;
        public String deviceId; public Long orgId; public String orgPath;

        public String idemKey() { return userId + ":" + punchDate + ":" + punchType; }
    }
}
