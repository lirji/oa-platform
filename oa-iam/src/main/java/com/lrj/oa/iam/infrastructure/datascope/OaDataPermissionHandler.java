package com.lrj.oa.iam.infrastructure.datascope;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.lrj.oa.security.annotation.DataScope;
import com.lrj.oa.security.model.DataScopeRule;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 把数据范围规则翻译成 SQL 条件，交给 MyBatis-Plus 注入到 WHERE 里。
 *
 * <p><b>核心性能决策（ADR-0007）</b>：{@code ORG_AND_SUB} 生成的是
 * {@code t.org_path LIKE '/1/23/%'} 前缀匹配，而不是把子部门展开成
 * {@code org_id IN (…5000 项…)}。万人级组织下，后者会让每条查询都拖着一个巨大的 IN 列表，
 * 既撑爆 SQL 长度也毁掉执行计划。
 *
 * <p>前缀过多（超过 {@link DataScopeRule#MAX_PREFIXES}）时降级为 {@code org_id = ANY(array)}，
 * 避免 WHERE 里挂一长串 OR。
 */
@Component
public class OaDataPermissionHandler implements MultiDataPermissionHandler {

    private static final Logger log = LoggerFactory.getLogger(OaDataPermissionHandler.class);

    /** 路径由建表 CHECK 约束保证只含数字与斜杠；这里再挡一道，杜绝任何拼接注入的可能。 */
    private static final Pattern SAFE_PATH = Pattern.compile("^/[0-9/]*$");

    private final DataScopeMetrics metrics;
    private final GovernedTableRegistry governedTables;

    @Autowired
    public OaDataPermissionHandler(DataScopeMetrics metrics, GovernedTableRegistry governedTables) {
        this.metrics = metrics;
        this.governedTables = governedTables;
    }

    /** 仅供不启动 Spring 的 SQL 单元测试使用。 */
    public OaDataPermissionHandler() {
        this(DataScopeMetrics.noop(), new GovernedTableRegistry());
    }

    public OaDataPermissionHandler(DataScopeMetrics metrics) {
        this(metrics, new GovernedTableRegistry());
    }

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        String tableName = table.getFullyQualifiedName();
        DataScopeContext.Active active = DataScopeContext.peek();
        if (active == null) {
            if (governedTables.contains(tableName)) {
                metrics.missing();
                metrics.denied();
                throw new IllegalStateException("受管表缺少 @DataScope 上下文: table=" + tableName
                        + " statement=" + mappedStatementId);
            }
            return null;
        }

        DataScope ann = active.annotation();
        DataScopeRule rule = active.rule();

        active.seen(tableName);
        if (!active.targets(tableName)) return null;

        // 目标表必须使用声明的别名；错配不能静默放行。
        String alias = table.getAlias() == null ? table.getName() : table.getAlias().getName();
        if (alias == null || !alias.equalsIgnoreCase(ann.alias())) {
            metrics.aliasMismatch();
            metrics.denied();
            throw new IllegalStateException("数据权限目标表别名不匹配: table=" + tableName
                    + " expected=" + ann.alias() + " actual=" + alias);
        }

        String condition;
        try {
            condition = buildCondition(ann, rule);
        } catch (RuntimeException e) {
            metrics.denied();
            throw e;
        }
        active.evaluated(condition != null);       // ALL 也必须记为明确求值，而不是未执行
        if (condition == null) return null;

        try {
            return CCJSqlParserUtil.parseCondExpression(condition);
        } catch (Exception e) {
            // 解析失败绝不能"放行"——那等于关掉数据权限。宁可让查询失败。
            metrics.denied();
            log.error("数据权限条件解析失败，拒绝执行该查询: {}", condition, e);
            throw new IllegalStateException("数据权限条件非法: " + condition, e);
        }
    }

    private String buildCondition(DataScope ann, DataScopeRule rule) {
        String a = ann.alias();
        return switch (rule.type()) {
            case ALL -> null;
            // 安全默认值：算不出范围就一行都不给，而不是全给
            case NONE -> "1 = 0";
            case SELF -> "%s.%s = '%s'".formatted(a, ann.userColumn(), escape(rule.selfUserId()));
            case ORG -> rule.orgIds().isEmpty() ? "1 = 0"
                    : "%s.%s IN (%s)".formatted(a, ann.orgColumn(), joinIds(rule.orgIds()));
            case ORG_AND_SUB, CUSTOM -> pathCondition(a, ann, rule);
        };
    }

    private String pathCondition(String alias, DataScope ann, DataScopeRule rule) {
        List<String> prefixes = rule.pathPrefixes();
        if (prefixes.isEmpty()) return "1 = 0";

        if (rule.shouldDegradeToIdList()) {
            // 前缀太多时改用 id 列表：一长串 OR LIKE 反而更糟
            log.debug("数据范围前缀 {} 个，超过阈值，降级为 id 列表", prefixes.size());
            return rule.orgIds().isEmpty() ? "1 = 0"
                    : "%s.%s IN (%s)".formatted(alias, ann.orgColumn(), joinIds(rule.orgIds()));
        }

        return prefixes.stream()
                .peek(OaDataPermissionHandler::assertSafePath)
                .map(p -> "%s.%s LIKE '%s%%'".formatted(alias, ann.pathColumn(), p))
                .collect(Collectors.joining(" OR ", "(", ")"));
    }

    private static void assertSafePath(String path) {
        if (!SAFE_PATH.matcher(path).matches()) {
            throw new IllegalStateException("组织路径格式非法，拒绝拼入 SQL: " + path);
        }
    }

    private static String joinIds(java.util.Collection<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static String escape(String v) {
        return v == null ? "" : v.replace("'", "''");
    }
}
