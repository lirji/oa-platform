package com.lrj.oa.iam.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lrj.oa.iam.domain.GrantRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface GrantMapper extends BaseMapper<GrantRecord> {

    /**
     * 取一个用户当前<b>可能</b>适用的全部授权。
     *
     * <p>时间窗过滤放在 SQL 里（已撤销、未生效、已过期一律不取）——
     * 这就是"定时角色到点自动失效"的实现：<b>不依赖任何定时任务</b>。
     *
     * <p>ORG_UNIT 主体这里先按"本人所在组织 + 其全部祖先"宽取，
     * {@code include_descendants} 的判断留到 Java 里做 —— 因为要区分
     * "授权正好落在本人所在组织"（永远适用）和"落在上级组织"（需 include_descendants）。
     */
    @Select("""
            <script>
            SELECT * FROM oa_iam.grant_record
             WHERE revoked_at IS NULL
               AND valid_from &lt;= #{now}
               AND (valid_to IS NULL OR valid_to &gt; #{now})
               AND (
                    (subject_type = 'USER' AND subject_id = #{userId})
                <if test="orgIds != null and orgIds.size() > 0">
                 OR (subject_type = 'ORG_UNIT' AND subject_id IN
                     <foreach item="o" collection="orgIds" open="(" separator="," close=")">#{o}</foreach>)
                </if>
                <if test="positionIds != null and positionIds.size() > 0">
                 OR (subject_type = 'POSITION' AND subject_id IN
                     <foreach item="p" collection="positionIds" open="(" separator="," close=")">#{p}</foreach>)
                </if>
                 OR (subject_type = 'USER_GROUP')
               )
            </script>
            """)
    List<GrantRecord> selectApplicable(@Param("userId") String userId,
                                       @Param("orgIds") Collection<String> orgIds,
                                       @Param("positionIds") Collection<String> positionIds,
                                       @Param("now") OffsetDateTime now);

    /** 下一个会改变判定结果的时间点（最近的 valid_to）。快照 TTL 不能越过它。 */
    @Select("""
            <script>
            SELECT min(valid_to) FROM oa_iam.grant_record
             WHERE revoked_at IS NULL AND valid_to IS NOT NULL AND valid_to &gt; #{now}
               AND (
                    (subject_type = 'USER' AND subject_id = #{userId})
                <if test="orgIds != null and orgIds.size() > 0">
                 OR (subject_type = 'ORG_UNIT' AND subject_id IN
                     <foreach item="o" collection="orgIds" open="(" separator="," close=")">#{o}</foreach>)
                </if>
               )
            </script>
            """)
    OffsetDateTime nextBoundary(@Param("userId") String userId,
                                @Param("orgIds") Collection<String> orgIds,
                                @Param("now") OffsetDateTime now);

    @Select("SELECT * FROM oa_iam.grant_record WHERE subject_type = #{type} AND subject_id = #{id} AND revoked_at IS NULL ORDER BY id DESC")
    List<GrantRecord> selectBySubject(@Param("type") String type, @Param("id") String id);

    @Update("""
            UPDATE oa_iam.grant_record
               SET revoked_at = now(), revoked_by = #{by}, revoke_reason = #{reason}
             WHERE id = #{id} AND revoked_at IS NULL
            """)
    int revoke(@Param("id") Long id, @Param("by") String by, @Param("reason") String reason);

    /** 到期回收任务用：列出刚过期但还没归档的授权。 */
    @Select("""
            SELECT * FROM oa_iam.grant_record
             WHERE revoked_at IS NULL AND valid_to IS NOT NULL AND valid_to <= now()
             ORDER BY valid_to LIMIT #{limit}
            """)
    List<GrantRecord> selectExpired(@Param("limit") int limit);
}
