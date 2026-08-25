package com.lrj.oa.iam.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lrj.oa.iam.domain.Delegation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DelegationMapper extends BaseMapper<Delegation> {

    /** 当前生效的结构化委托规则。查询和办理必须消费同一批规则。 */
    @Select("""
            SELECT * FROM oa_iam.delegation
             WHERE tenant_id = #{tenantId} AND delegatee_user_id = #{userId} AND status = 'ACTIVE'
               AND valid_from <= now() AND (valid_to IS NULL OR valid_to > now())
            """)
    List<Delegation> selectActiveForDelegatee(@Param("tenantId") long tenantId,
                                               @Param("userId") String userId);

    default List<String> selectDelegatorsOf(long tenantId, String userId) {
        return selectActiveForDelegatee(tenantId, userId).stream()
                .map(Delegation::getDelegatorUserId).distinct().toList();
    }

    @Select("SELECT * FROM oa_iam.delegation WHERE tenant_id = #{tenantId} AND delegator_user_id = #{userId} ORDER BY id DESC")
    List<Delegation> selectByDelegator(@Param("tenantId") long tenantId, @Param("userId") String userId);

    @Update("UPDATE oa_iam.delegation SET status = 'REVOKED' WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'ACTIVE'")
    int revoke(@Param("tenantId") long tenantId, @Param("id") Long id);
}
