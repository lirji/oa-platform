package com.lrj.oa.org.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lrj.oa.org.domain.ReportingLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

@Mapper
public interface ReportingLineMapper extends BaseMapper<ReportingLine> {

    /** 当前实线上级的 userId。审批"逐级上报"沿着它往上走。 */
    @Select("""
            SELECT m.user_id FROM oa_org.reporting_line r
              JOIN oa_org.employee m ON m.id = r.manager_employee_id
             WHERE r.employee_id = #{employeeId} AND r.type = 'SOLID' AND r.valid_to IS NULL
               AND m.status <> 'LEFT'
             LIMIT 1
            """)
    String selectSolidManagerUserId(@Param("employeeId") Long employeeId);

    @Select("""
            SELECT r.manager_employee_id FROM oa_org.reporting_line r
              JOIN oa_org.employee m ON m.id = r.manager_employee_id
             WHERE r.employee_id = #{employeeId} AND r.type = 'SOLID' AND r.valid_to IS NULL
               AND m.status <> 'LEFT'
             LIMIT 1
            """)
    Long selectSolidManagerId(@Param("employeeId") Long employeeId);

    @Update("UPDATE oa_org.reporting_line SET valid_to = #{validTo} WHERE employee_id = #{employeeId} AND type = #{type} AND valid_to IS NULL")
    int closeActive(@Param("employeeId") Long employeeId, @Param("type") String type, @Param("validTo") LocalDate validTo);
}
