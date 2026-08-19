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

    /** 我正在代理谁。待办查询用它扩展 assignee 集合。 */
    @Select("""
            SELECT delegator_user_id FROM oa_iam.delegation
             WHERE delegatee_user_id = #{userId} AND status = 'ACTIVE'
               AND valid_from <= now() AND (valid_to IS NULL OR valid_to > now())
            """)
    List<String> selectDelegatorsOf(@Param("userId") String userId);

    @Select("SELECT * FROM oa_iam.delegation WHERE delegator_user_id = #{userId} ORDER BY id DESC")
    List<Delegation> selectByDelegator(@Param("userId") String userId);

    @Update("UPDATE oa_iam.delegation SET status = 'REVOKED' WHERE id = #{id} AND status = 'ACTIVE'")
    int revoke(@Param("id") Long id);
}
