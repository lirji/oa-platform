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
 *   <li>非目标 join 表不注入；目标表别名不匹配必须拒绝。</li>
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
    @DisplayName("★ 受管表没有 @DataScope 上下文时拒绝执行")
    void governed_table_without_context_is_rejected() {
        Table table = new Table("oa_org.v_employee_directory");
        table.setAlias(new net.sf.jsqlparser.expression.Alias("t"));
        assertThatThrownBy(() -> handler.getSqlSegment(table, null, "DirectoryMapper.search"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("受管表缺少");
    }

    @Test
    @DisplayName("ALL 范围不加条件")
    void all_scope_adds_nothing() {
        DataScopeContext.set(scope("t"), DataScopeRule.all());
        assertThat(handler.getSqlSegment(tableAliased("t"), null, "stmt")).isNull();
        assertThat(DataScopeContext.peek().evaluated()).isTrue();
        assertThat(DataScopeContext.peek().predicateInjected()).isFalse();
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
    @DisplayName("SELECT/COUNT/UPDATE/DELETE 使用同一行级谓词")
    void read_and_write_operations_share_the_same_predicate() {
        DataScopeRule rule = new DataScopeRule(
                DataScopeType.ORG, List.of(), Set.of(10L, 20L), "u-1");
        for (String statement : List.of("selectPage", "selectCount", "updateById", "deleteById")) {
            DataScopeContext.clear();
            DataScopeContext.set(scope("t"), rule);
            String condition = handler.getSqlSegment(tableAliased("t"), null, statement).toString();
            assertThat(condition).as(statement).contains("t.org_id").contains("10").contains("20");
        }
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
    @DisplayName("★ 前缀过多时不能用不完整的锚点 id 降级而丢失下级组织")
    void too_many_prefixes_preserve_descendant_semantics() {
        List<String> many = List.of("/1/1/", "/1/2/", "/1/3/", "/1/4/",
                                    "/1/5/", "/1/6/", "/1/7/", "/1/8/", "/1/9/");
        assertThat(many.size()).isGreaterThan(DataScopeRule.MAX_PREFIXES);
        DataScopeContext.set(scope("t"), new DataScopeRule(
                DataScopeType.ORG_AND_SUB, many, Set.of(1L, 2L, 3L), "u-1"));
        String sql = handler.getSqlSegment(tableAliased("t"), null, "stmt").toString();
        assertThat(sql).contains("/1/1/%").contains("/1/9/%").contains("LIKE");
    }

    @Test
    @DisplayName("CUSTOM 精确组织在不包含下级时仍按 org_id 生效")
    void custom_exact_orgs_use_id_predicate() {
        DataScopeContext.set(scope("t"), new DataScopeRule(
                DataScopeType.CUSTOM, List.of(), Set.of(23L, 45L), null));
        String sql = handler.getSqlSegment(tableAliased("t"), null, "stmt").toString();
        assertThat(sql).contains("org_id").contains("23").contains("45").doesNotContain("LIKE");
    }

    @Test
    @DisplayName("混合范围用 OR 保留子树、精确组织和本人三部分")
    void custom_union_keeps_all_components() {
        DataScopeContext.set(scope("t"), new DataScopeRule(
                DataScopeType.CUSTOM, List.of("/1/2/"), Set.of(45L), "u-1"));
        String sql = handler.getSqlSegment(tableAliased("t"), null, "stmt").toString();
        assertThat(sql).contains("/1/2/%").contains("org_id").contains("45")
                .contains("user_id").contains("u-1").contains("OR");
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
    @DisplayName("★ 目标表别名不匹配时 fail-closed")
    void target_table_alias_mismatch_is_rejected() {
        DataScopeContext.set(scope("t"), new DataScopeRule(
                DataScopeType.ORG_AND_SUB, List.of("/1/23/"), Set.of(23L), "u-1"));
        assertThatThrownBy(() -> handler.getSqlSegment(tableAliased("other"), null, "stmt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("别名不匹配");
    }

    @Test
    @DisplayName("join 中非目标表不注入")
    void non_target_join_table_is_untouched() {
        DataScopeContext.set(scope("t"), new DataScopeRule(
                DataScopeType.ORG_AND_SUB, List.of("/1/23/"), Set.of(23L), "u-1"));
        Table other = new Table("oa_org.position");
        other.setAlias(new net.sf.jsqlparser.expression.Alias("p"));
        assertThat(handler.getSqlSegment(other, null, "stmt")).isNull();
        assertThat(DataScopeContext.peek().evaluated()).isFalse();
        assertThat(DataScopeContext.peek().seenTables()).contains("oa_org.position");
    }

    @Test
    @DisplayName("嵌套数据权限上下文结束后恢复外层")
    void nested_context_restores_outer_scope() {
        DataScope outerScope = scope("t");
        DataScope innerScope = scope("x");
        DataScopeContext.Active outer = DataScopeContext.push(outerScope, DataScopeRule.none());
        DataScopeContext.Active inner = DataScopeContext.push(innerScope, DataScopeRule.all());
        assertThat(DataScopeContext.peek()).isSameAs(inner);
        DataScopeContext.pop(inner);
        assertThat(DataScopeContext.peek()).isSameAs(outer);
        DataScopeContext.pop(outer);
        assertThat(DataScopeContext.peek()).isNull();
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
            @Override public String permission() { return "oa:employee:view"; }
            @Override public String table() { return "v_employee_directory"; }
            @Override public String alias() { return alias; }
            @Override public String orgColumn() { return "org_id"; }
            @Override public String pathColumn() { return "org_path"; }
            @Override public String userColumn() { return "user_id"; }
            @Override public String module() { return "org"; }
        };
    }
}
