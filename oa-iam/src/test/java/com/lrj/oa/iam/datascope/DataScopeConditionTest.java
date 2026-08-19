package com.lrj.oa.iam.datascope;

import com.lrj.oa.iam.infrastructure.datascope.DataScopeContext;
import com.lrj.oa.iam.infrastructure.datascope.OaDataPermissionHandler;
import com.lrj.oa.security.annotation.DataScope;
import com.lrj.oa.security.model.DataScopeRule;
import com.lrj.oa.security.model.DataScopeType;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 数据权限的 SQL 生成 —— 行级越权就出在这里，必须有单测守着。
 *
 * <p>三条最关键的性质：
 * <ol>
 *   <li>算不出范围时生成 {@code 1 = 0}（<b>一行都不给</b>），而不是不加条件；</li>
 *   <li>{@code ORG_AND_SUB} 生成路径前缀 {@code LIKE}，而不是巨大的 {@code IN} 列表（ADR-0007）；</li>
 *   <li>别名对不上的表<b>不注入</b>，避免误伤 join 进来的其它表。</li>
 * </ol>
 */
class DataScopeConditionTest {

    private final OaDataPermissionHandler handler = new OaDataPermissionHandler();

    @AfterEach
    void clear() { DataScopeContext.clear(); }

    @Test
    @DisplayName("没有 @DataScope 上下文时不注入任何条件")
    void no_context_means_no_injection() {
        assertThat(handler.getSqlSegment(tableAliased("t"), null, "stmt")).isNull();
    }

    @Test
    @DisplayName("ALL 范围不加条件")
    void all_scope_adds_nothing() {
        DataScopeContext.set(scope("t"), DataScopeRule.all());
        assertThat(handler.getSqlSegment(tableAliased("t"), null, "stmt")).isNull();
    }

    @Test
    @DisplayName("★ NONE 范围必须生成 1 = 0（安全默认值是一行都不给）")
    void none_scope_denies_everything() {
        DataScopeContext.set(scope("t"), DataScopeRule.none());
        Expression e = handler.getSqlSegment(tableAliased("t"), null, "stmt");
        assertThat(e).isNotNull();
        assertThat(e.toString().replace(" ", ""))
                .as("算不出范围时若返回 null 就等于放行全部数据，那是全量泄露")
                .isEqualTo("1=0");
    }

    @Test
    @DisplayName("★ ORG_AND_SUB 生成路径前缀 LIKE，不是 IN 大列表")
    void org_and_sub_uses_path_prefix() {
        DataScopeContext.set(scope("t"), new DataScopeRule(
                DataScopeType.ORG_AND_SUB, List.of("/1/23/"), Set.of(23L), "u-1"));
        String sql = handler.getSqlSegment(tableAliased("t"), null, "stmt").toString();
        assertThat(sql).contains("org_path").contains("LIKE").contains("/1/23/%");
        assertThat(sql).as("万人级下展开成 IN 列表会撑爆 SQL 并毁掉执行计划").doesNotContain(" IN ");
    }

    @Test
    @DisplayName("多个互不覆盖的范围用 OR 拼接")
    void multiple_prefixes_are_or_ed() {
        DataScopeContext.set(scope("t"), new DataScopeRule(
                DataScopeType.ORG_AND_SUB, List.of("/1/2/", "/1/5/"), Set.of(2L, 5L), "u-1"));
        String sql = handler.getSqlSegment(tableAliased("t"), null, "stmt").toString();
        assertThat(sql).contains("/1/2/%").contains("/1/5/%").contains("OR");
    }

    @Test
    @DisplayName("前缀过多时降级为 id 列表，避免 WHERE 里挂一长串 OR")
    void too_many_prefixes_degrade_to_id_list() {
        List<String> many = List.of("/1/1/", "/1/2/", "/1/3/", "/1/4/",
                                    "/1/5/", "/1/6/", "/1/7/", "/1/8/", "/1/9/");
        assertThat(many.size()).isGreaterThan(DataScopeRule.MAX_PREFIXES);
        DataScopeContext.set(scope("t"), new DataScopeRule(
                DataScopeType.CUSTOM, many, Set.of(1L, 2L, 3L), "u-1"));
        String sql = handler.getSqlSegment(tableAliased("t"), null, "stmt").toString();
        assertThat(sql).contains("org_id").contains("IN");
        assertThat(sql).doesNotContain("LIKE");
    }

    @Test
    @DisplayName("SELF 范围按创建人过滤，且用户 id 里的引号会被转义")
    void self_scope_filters_by_user_and_escapes() {
        DataScopeContext.set(scope("t"), new DataScopeRule(
                DataScopeType.SELF, List.of(), Set.of(), "o'brien"));
        String sql = handler.getSqlSegment(tableAliased("t"), null, "stmt").toString();
        assertThat(sql).contains("user_id").contains("o''brien");
    }

    @Test
    @DisplayName("★ 别名对不上的表不注入（不能误伤 join 进来的其它表）")
    void other_tables_are_untouched() {
        DataScopeContext.set(scope("t"), new DataScopeRule(
                DataScopeType.ORG_AND_SUB, List.of("/1/23/"), Set.of(23L), "u-1"));
        assertThat(handler.getSqlSegment(tableAliased("other"), null, "stmt")).isNull();
    }

    @Test
    @DisplayName("★ 非法组织路径拒绝拼入 SQL")
    void malicious_path_is_rejected() {
        DataScopeContext.set(scope("t"), new DataScopeRule(
                DataScopeType.ORG_AND_SUB, List.of("/1/' OR '1'='1"), Set.of(1L), "u-1"));
        assertThatThrownBy(() -> handler.getSqlSegment(tableAliased("t"), null, "stmt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("组织路径格式非法");
    }

    // ── 测试夹具 ────────────────────────────────────────

    private static Table tableAliased(String alias) {
        Table t = new Table("v_employee_directory");
        t.setAlias(new net.sf.jsqlparser.expression.Alias(alias));
        return t;
    }

    /** 手写一个 @DataScope 实例，避免为测试引入代理框架。 */
    private static DataScope scope(String alias) {
        return new DataScope() {
            @Override public Class<? extends Annotation> annotationType() { return DataScope.class; }
            @Override public String alias() { return alias; }
            @Override public String orgColumn() { return "org_id"; }
            @Override public String pathColumn() { return "org_path"; }
            @Override public String userColumn() { return "user_id"; }
            @Override public String module() { return "org"; }
        };
    }
}
