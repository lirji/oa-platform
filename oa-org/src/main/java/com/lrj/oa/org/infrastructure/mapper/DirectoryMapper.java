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

    /**
     * 游标分页：按 sync_seq 递增翻页。
     *
     * <p>用游标而不是 OFFSET：万人表上 {@code OFFSET 9000} 要先扫过前 9000 行；
     * 而且翻页期间有人入职会让某一行被跳过或重复（OFFSET 的经典问题）。
     * 游标基于单调水位线，两个毛病都没有。
     *
     * <p>★ 仍然带 {@code @DataScope}（在服务层）—— 分页不能成为绕过数据权限的口子。
     */
    @Select("""
            <script>
            SELECT t.employee_id, t.user_id, t.emp_no, t.name, t.email, t.status, t.hire_date,
                   t.org_id, t.org_path, t.org_name, t.position_name, t.is_leader AS leader,
                   t.mobile_enc, t.sync_seq
              FROM oa_org.v_employee_directory t
            <where>
              <if test="cursor != null"> t.sync_seq &gt; #{cursor} </if>
              <if test="keyword != null and keyword != ''">
                AND (t.name LIKE '%' || #{keyword} || '%' OR t.emp_no LIKE #{keyword} || '%')
              </if>
            </where>
             ORDER BY t.sync_seq
             LIMIT #{size}
            </script>
            """)
    List<DirectoryRow> page(@Param("cursor") Long cursor, @Param("keyword") String keyword,
                            @Param("size") int size);

    /** 自 since 之后发生变化的行。与 page 共用一套水位线语义。 */
    @Select("""
            SELECT t.employee_id, t.user_id, t.emp_no, t.name, t.email, t.status, t.hire_date,
                   t.org_id, t.org_path, t.org_name, t.position_name, t.is_leader AS leader,
                   t.mobile_enc, t.sync_seq
              FROM oa_org.v_employee_directory t
             WHERE t.sync_seq > #{since}
             ORDER BY t.sync_seq
             LIMIT #{size}
            """)
    List<DirectoryRow> changedSince(@Param("since") long since, @Param("size") int size);

    /**
     * 墓碑：自 since 之后离职或失去主岗的人，客户端应从本地删掉。
     *
     * <p>★ 这条<b>刻意不带数据权限</b>：告诉客户端"把这个 id 删掉"不泄露任何信息
     * （他本来就在客户端的本地缓存里）。反过来，如果墓碑也被数据权限过滤，
     * 一个人调岗出我的可见范围时我收不到墓碑，本地就会永远留着他 —— 那才是泄露。
     */
    @Select("""
            SELECT employee_id, user_id, sync_seq
              FROM oa_org.v_directory_tombstone
             WHERE sync_seq > #{since}
             ORDER BY sync_seq
             LIMIT #{size}
            """)
    List<java.util.Map<String, Object>> tombstonesSince(@Param("since") long since, @Param("size") int size);

    /** 当前最大水位线。客户端把它存下来作为下次 since。 */
    @Select("SELECT coalesce(last_value, 0) FROM oa_org.directory_sync_seq")
    long currentWatermark();
}
