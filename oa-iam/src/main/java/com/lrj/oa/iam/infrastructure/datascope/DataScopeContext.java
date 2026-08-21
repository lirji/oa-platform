package com.lrj.oa.iam.infrastructure.datascope;

import com.lrj.oa.security.annotation.DataScope;
import com.lrj.oa.security.model.DataScopeRule;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 当前线程的数据权限执行栈。切面负责 push/pop，MyBatis handler 记录真实表和求值结果。
 *
 * <p>执行记录必须区分“ALL 明确放行”和“根本没有执行策略”；前者 evaluated=true、predicate=false，
 * 后者在 strict 模式下拒绝。栈结构保证嵌套 scoped service 不会覆盖外层上下文。
 */
public final class DataScopeContext {

    public static final class Active {
        private final DataScope annotation;
        private final DataScopeRule rule;
        private final Set<String> seenTables = new LinkedHashSet<>();
        private boolean evaluated;
        private boolean predicateInjected;

        private Active(DataScope annotation, DataScopeRule rule) {
            this.annotation = annotation;
            this.rule = rule;
        }

        public DataScope annotation() { return annotation; }
        public DataScopeRule rule() { return rule; }
        public Set<String> seenTables() { return Set.copyOf(seenTables); }
        public boolean evaluated() { return evaluated; }
        public boolean predicateInjected() { return predicateInjected; }
        public void seen(String table) { seenTables.add(normalize(table)); }
        public boolean targets(String table) {
            return normalize(annotation.table()).equals(normalize(table));
        }
        public void evaluated(boolean injected) {
            evaluated = true;
            predicateInjected |= injected;
        }
    }

    private static final ThreadLocal<Deque<Active>> HOLDER = ThreadLocal.withInitial(ArrayDeque::new);

    private DataScopeContext() {}

    public static Active push(DataScope annotation, DataScopeRule rule) {
        Active active = new Active(annotation, rule);
        HOLDER.get().push(active);
        return active;
    }

    public static Active peek() {
        Deque<Active> stack = HOLDER.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    public static void pop(Active expected) {
        Deque<Active> stack = HOLDER.get();
        if (!stack.isEmpty() && stack.peek() == expected) stack.pop();
        else stack.remove(expected);
        if (stack.isEmpty()) HOLDER.remove();
    }

    /** 测试兼容入口。 */
    public static void set(DataScope annotation, DataScopeRule rule) { push(annotation, rule); }
    public static boolean wasConsumed() { Active active = peek(); return active != null && active.evaluated(); }
    public static void clear() { HOLDER.remove(); }

    private static String normalize(String table) {
        if (table == null) return "";
        return table.replace("\"", "").trim().toLowerCase(Locale.ROOT);
    }
}
