package com.lrj.oa.iam.aspect;

import com.lrj.oa.security.model.DataScopeRule;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 当前调用栈已经通过接口 RBAC/ABAC 判定的权限点及其本次有效来源范围。 */
public final class AuthorizationContext {
    private record Frame(Set<String> permissions, Map<String, DataScopeRule> effectiveScopes) {}

    private static final ThreadLocal<Deque<Frame>> HOLDER =
            ThreadLocal.withInitial(ArrayDeque::new);

    private AuthorizationContext() {}

    static void push(Set<String> permissions) { push(permissions, Map.of()); }

    static void push(Set<String> permissions, Map<String, DataScopeRule> effectiveScopes) {
        HOLDER.get().push(new Frame(Set.copyOf(permissions), Map.copyOf(effectiveScopes)));
    }

    public static boolean allows(String permission) {
        Deque<Frame> stack = HOLDER.get();
        return !stack.isEmpty() && stack.peek().permissions().contains(permission);
    }

    public static Optional<DataScopeRule> effectiveScope(String permission) {
        Deque<Frame> stack = HOLDER.get();
        return stack.isEmpty() ? Optional.empty()
                : Optional.ofNullable(stack.peek().effectiveScopes().get(permission));
    }

    static void pop() {
        Deque<Frame> stack = HOLDER.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) HOLDER.remove();
    }

    static void clear() { HOLDER.remove(); }
}
