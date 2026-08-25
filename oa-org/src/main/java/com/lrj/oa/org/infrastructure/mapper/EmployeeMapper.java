package com.lrj.oa.org.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lrj.oa.org.domain.Employee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {

    @Select("SELECT * FROM oa_org.employee WHERE tenant_id = #{tenantId} AND user_id = #{userId}")
    Employee selectByUserId(@Param("tenantId") long tenantId, @Param("userId") String userId);

    default Employee selectByUserId(String userId) {
        return selectByUserId(com.lrj.oa.common.context.TenantContext.get(), userId);
    }

    @Select("SELECT * FROM oa_org.employee WHERE tenant_id = #{tenantId} AND id = #{id}")
    Employee selectTenantById(@Param("tenantId") long tenantId, @Param("id") Long id);

    @Select("SELECT * FROM oa_org.employee WHERE tenant_id = #{tenantId} AND emp_no = #{empNo}")
    Employee selectByEmpNo(@Param("tenantId") Long tenantId, @Param("empNo") String empNo);

    @Select("SELECT user_id FROM oa_org.employee WHERE tenant_id = #{tenantId} AND id = #{id}")
    String selectUserIdById(@Param("tenantId") long tenantId, @Param("id") Long id);

    default String selectUserIdById(Long id) {
        return selectUserIdById(com.lrj.oa.common.context.TenantContext.get(), id);
    }
}
