package com.lrj.oa.org.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lrj.oa.org.domain.Employee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {

    @Select("SELECT * FROM oa_org.employee WHERE user_id = #{userId}")
    Employee selectByUserId(@Param("userId") String userId);

    @Select("SELECT * FROM oa_org.employee WHERE tenant_id = #{tenantId} AND emp_no = #{empNo}")
    Employee selectByEmpNo(@Param("tenantId") Long tenantId, @Param("empNo") String empNo);

    @Select("SELECT user_id FROM oa_org.employee WHERE id = #{id}")
    String selectUserIdById(@Param("id") Long id);
}
