package com.lrj.oa.org.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lrj.oa.org.domain.EmployeeOrgAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AssignmentMapper extends BaseMapper<EmployeeOrgAssignment> {

    @Select("""
            SELECT * FROM oa_org.employee_org_assignment
             WHERE employee_id = #{employeeId} AND valid_to IS NULL
             ORDER BY CASE assignment_type WHEN 'PRIMARY' THEN 0 WHEN 'CONCURRENT' THEN 1 ELSE 2 END, id
            """)
    List<EmployeeOrgAssignment> selectActiveByEmployee(@Param("employeeId") Long employeeId);

    /**
     * as-of 历史还原：闭开区间 [valid_from, valid_to)。
     * 这是"历史审批要能还原当时组织归属"的技术底座。
     */
    @Select("""
            SELECT * FROM oa_org.employee_org_assignment
             WHERE employee_id = #{employeeId}
               AND valid_from <= #{asOf}
               AND (valid_to IS NULL OR valid_to > #{asOf})
             ORDER BY CASE assignment_type WHEN 'PRIMARY' THEN 0 WHEN 'CONCURRENT' THEN 1 ELSE 2 END, id
            """)
    List<EmployeeOrgAssignment> selectAsOf(@Param("employeeId") Long employeeId, @Param("asOf") LocalDate asOf);

    @Select("""
            SELECT e.user_id FROM oa_org.employee_org_assignment a
              JOIN oa_org.employee e ON e.id = a.employee_id
             WHERE a.org_unit_id = #{orgId} AND a.is_leader = true AND a.valid_to IS NULL
               AND e.status <> 'LEFT'
            """)
    List<String> selectLeaderUserIds(@Param("orgId") Long orgId);

    /** 关闭一段任职：写 valid_to，不删行。 */
    @Update("UPDATE oa_org.employee_org_assignment SET valid_to = #{validTo} WHERE id = #{id} AND valid_to IS NULL")
    int close(@Param("id") Long id, @Param("validTo") LocalDate validTo);
}
