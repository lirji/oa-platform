package com.lrj.oa.iam.domain;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** 统一身份类型。USER 只能由员工投影创建，其余为非人身份。 */
public enum IdentityType {
    USER,
    SERVICE_ACCOUNT,
    API_CLIENT,
    APPLICATION,
    AGENT,
    BOT,
    AUTOMATION_WORKER;

    private static final Set<String> NAMES = Arrays.stream(values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    public static boolean isKnown(String raw) {
        return raw != null && NAMES.contains(raw);
    }

    public boolean human() {
        return this == USER;
    }
}
