package com.lrj.oa.iam.infrastructure.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 权限缓存的版本真值源。
 *
 * <p>为什么放数据库而不是只放 Redis：Redis 挂掉或被清空后，epoch 归零会让<b>陈旧快照被当成有效</b> ——
 * 那是安全事故。数据库是权威，Redis Pub/Sub 只是让其它节点更快知道。
 */
@Mapper
public interface PermVersionMapper {

    @Select("""
            SELECT coalesce((SELECT epoch FROM oa_iam.tenant_perm_epoch WHERE tenant_id=#{tenantId}), 0)
            """)
    long currentEpoch(@Param("tenantId") long tenantId);

    @Select("SELECT epoch FROM oa_iam.perm_epoch WHERE id=1")
    long currentLegacyEpoch();

    /**
     * Advances both versions atomically. The legacy singleton remains a rolling-deployment fence:
     * old nodes poll it and therefore still reject stale snapshots while versions are mixed.
     */
    @Select("""
            WITH legacy AS (
                UPDATE oa_iam.perm_epoch SET epoch=epoch+1, updated_at=now() WHERE id=1 RETURNING epoch
            )
            INSERT INTO oa_iam.tenant_perm_epoch(tenant_id, epoch)
            VALUES (#{tenantId}, 1)
            ON CONFLICT (tenant_id) DO UPDATE
                SET epoch=oa_iam.tenant_perm_epoch.epoch+1, updated_at=now()
            RETURNING epoch
            """)
    long bumpEpoch(@Param("tenantId") long tenantId);

    @Select("SELECT coalesce((SELECT version FROM oa_iam.perm_user_version WHERE user_id = #{userId}), 0)")
    long userVersion(@Param("userId") String userId);

    @Insert("""
            INSERT INTO oa_iam.perm_user_version(user_id, version) VALUES (#{userId}, 1)
            ON CONFLICT (user_id) DO UPDATE SET version = oa_iam.perm_user_version.version + 1, updated_at = now()
            """)
    int bumpUserVersion(@Param("userId") String userId);
}
