package com.lrj.oa.org.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 通讯录查询。
 *
 * <p><b>表别名固定为 {@code t}</b>，与 {@code @DataScope(alias = "t")} 对应 ——
 * MyBatis-Plus 的数据权限拦截器按别名匹配注入，别名对不上就等于没有过滤。
 * 改这里的别名务必同步改注解。
 */
@Mapper
public interface DirectoryMapper {

    /**
     * ⚠️ 不要写成 {@code WHERE (#{keyword} IS NULL OR ...)}：PostgreSQL 无法为裸参数
     * 推断类型，会直接报 {@code could not determine data type of parameter $1}，
     * 整条查询失败。而失败的表现是接口返回 500、字段全 null —— 很容易被误读成"数据为空"。
     * 用动态 SQL 把条件整段去掉，既避开类型推断，也省掉一次无谓的比较。
     */
    @Select("""
            <script>
            SELECT t.employee_id, t.user_id, t.emp_no, t.name, t.email, t.status, t.hire_date,
                   t.org_id, t.org_path, t.org_name, t.position_name, t.is_leader AS leader,
                   t.mobile_enc
              FROM oa_org.v_employee_directory t
            <where>
              <if test="keyword != null and keyword != ''">
                (t.name LIKE '%' || #{keyword} || '%' OR t.emp_no LIKE #{keyword} || '%')
              </if>
            </where>
             ORDER BY t.org_depth, t.employee_id
             LIMIT #{limit}
            </script>
            """)
    List<DirectoryRow> search(@Param("keyword") String keyword, @Param("limit") int limit);

    @Select("SELECT count(*) FROM oa_org.v_employee_directory t")
    long countVisible();
}
