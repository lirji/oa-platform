package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.domain.AbacBranch;
import com.lrj.oa.iam.domain.PermissionSnapshot;
import com.lrj.oa.security.context.UserContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.regex.Pattern;

/** 受限 ABAC 求值：只读快照和方法参数，运行期零 DB/零远程。 */
@Component
public class AbacEvaluator {
    private static final Logger log = LoggerFactory.getLogger(AbacEvaluator.class);
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?i)(T\\s*\\(|\\bnew\\s+|@[A-Za-z_$]|#(?:root|this)\\b|\\.\\s*(?:class|classLoader|protectionDomain)\\b|"
                    + "\\b(?:getClass|Runtime|ProcessBuilder|ClassLoader|System)\\b|[;{}]|\\?\\[|!\\[|\\^\\[|\\$\\[)");
    private static final Pattern METHOD_CALL = Pattern.compile("(?:\\.|\\b)[A-Za-z_$][A-Za-z0-9_$]*\\s*\\(");
    private static final Pattern ASSIGNMENT = Pattern.compile("(?<![<>=!])=(?!=)");

    private final boolean enabled;
    private final PermissionCatalog catalog;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final Cache<String, Expression> compiled = Caffeine.newBuilder().maximumSize(10_000).build();
    private final Counter evaluations;
    private final Counter denied;
    private final Counter errors;

    public AbacEvaluator(PermissionCatalog catalog, ObjectProvider<MeterRegistry> meters,
                         @Value("${oa.iam.abac.enabled:false}") boolean enabled) {
        this.catalog = catalog;
        this.enabled = enabled;
        MeterRegistry registry = meters.getIfAvailable();
        evaluations = registry == null ? null : registry.counter("oa_iam_abac_evaluations_total");
        denied = registry == null ? null : registry.counter("oa_iam_abac_denied_total");
        errors = registry == null ? null : registry.counter("oa_iam_abac_errors_total");
        log.info("ABAC 条件引擎：{}", enabled ? "启用" : "关闭（保持 RBAC 兼容）");
    }

    public boolean enabled() { return enabled; }

    public void validate(String expression) {
        if (expression == null || expression.isBlank()) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "ABAC 表达式不能为空");
        }
        String value = expression.trim();
        if (value.length() > 512) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "ABAC 表达式最长 512 字符");
        }
        if (FORBIDDEN.matcher(value).find() || METHOD_CALL.matcher(value).find()
                || ASSIGNMENT.matcher(value).find()) {
            throw BusinessException.of(ResultCode.BAD_REQUEST,
                    "表达式包含被禁止的类型/Bean/构造器/方法/赋值或集合执行语法");
        }
        try {
            parser.parseExpression(value);
        } catch (RuntimeException e) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "ABAC 表达式语法错误: " + e.getMessage());
        }
    }

    /** 多 branch 为 OR；每个 branch 内 conditions 为 AND；无条件来源优先放行。 */
    public boolean allowed(PermissionSnapshot snapshot, String permCode, Method method,
                           Object[] args, UserContext user) {
        if (!enabled) return true;
        int permId = catalog.idOf(permCode);
        if (permId < 0) return false;
        if (snapshot.abacUnconditional(permId)) return true;
        List<AbacBranch> branches = snapshot.abacBranches().get(permId);
        if (branches == null || branches.isEmpty()) {
            increment(denied);
            return false;
        }
        increment(evaluations);
        for (AbacBranch branch : branches) {
            boolean branchAllowed = true;
            for (AbacBranch.Condition condition : branch.conditions()) {
                if (!evaluate(condition, method, args, user, permCode)) {
                    branchAllowed = false;
                    break;
                }
            }
            if (branchAllowed) return true;
        }
        increment(denied);
        return false;
    }

    private boolean evaluate(AbacBranch.Condition condition, Method method, Object[] args,
                             UserContext user, String permCode) {
        try {
            Expression expression = compiled.get(
                    condition.id() + ":" + condition.expression(), key -> {
                        validate(condition.expression());
                        return parser.parseExpression(condition.expression());
                    });
            SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
            context.setVariable("user", new AbacUser(user.userId(), user.username(), user.employeeId(),
                    user.primaryOrgId(), user.primaryOrgPath(), user.tenantId()));
            context.setVariable("args", args);
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < args.length; i++) {
                context.setVariable("p" + i, args[i]);
                context.setVariable("a" + i, args[i]);
                if (i < parameters.length && parameters[i].isNamePresent()) {
                    context.setVariable(parameters[i].getName(), args[i]);
                }
            }
            return Boolean.TRUE.equals(expression.getValue(context, Boolean.class));
        } catch (RuntimeException e) {
            increment(errors);
            log.warn("ABAC 求值失败，按拒绝处理 condition={} permission={} error={}",
                    condition.id(), permCode, e.getClass().getSimpleName());
            return false;
        }
    }

    private static void increment(Counter counter) { if (counter != null) counter.increment(); }

    public record AbacUser(String userId, String username, Long employeeId,
                           Long primaryOrgId, String primaryOrgPath, long tenantId) {}
}
