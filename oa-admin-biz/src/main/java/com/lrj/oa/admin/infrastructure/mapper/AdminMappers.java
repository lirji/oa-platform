package com.lrj.oa.admin.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 行政域 Mapper。必须放在 {@code ..infrastructure.mapper} 包下（@MapperScan 只扫这个通配）。
 *
 * <p>★ 需要数据权限的查询<b>必须</b>走 MyBatis：{@code @DataScope} 靠 MyBatis 拦截器改写 SQL，
 * 用 JdbcTemplate 手写会绕过它，注解形同虚设而且完全没有报错。
 */
public final class AdminMappers {

    private AdminMappers() {}

    public static class AssetRow {
        public Long id;
        public String assetNo;
        public String name;
        public String category;
        public String status;
        public String holderId;
        public Long orgId;
    }

    @Mapper
    public interface AssetQueryMapper {
        /** 别名 a 要与 {@code @DataScope(alias = "a")} 对上，拦截器按别名拼 org_path 前缀条件。 */
        @Select("""
                <script>
                SELECT a.id, a.asset_no AS assetNo, a.name, a.category, a.status,
                       a.holder_id AS holderId, a.org_id AS orgId
                  FROM oa_admin.asset a
                 WHERE 1 = 1
                 <if test="status != null and status != ''"> AND a.status = #{status} </if>
                 ORDER BY a.id
                 LIMIT #{limit}
                </script>
                """)
        List<AssetRow> search(@Param("status") String status, @Param("limit") int limit);
    }
}
