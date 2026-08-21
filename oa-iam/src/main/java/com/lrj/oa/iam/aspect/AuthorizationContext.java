package com.lrj.oa.iam.aspect;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

/** 当前调用栈已经通过接口 RBAC/ABAC 判定的权限点。 */
final class AuthorizationContext {
    private static final ThreadLocal<Deque<Set<String>>> HOLDER =
            ThreadLocal.withInitial(ArrayDeque::new);

    private AuthorizationContext() {}

    static void push(Set<String> permissions) { HOLDER.get().push(Set.copyOf(permissions)); }

    static boolean allows(String permission) {
        Deque<Set<String>> stack = HOLDER.get();
        return !stack.isEmpty() && stack.peek().contains(permission);
    }

    static void pop() {
        Deque<Set<String>> stack = HOLDER.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) HOLDER.remove();
    }

    static void clear() { HOLDER.remove(); }
}
