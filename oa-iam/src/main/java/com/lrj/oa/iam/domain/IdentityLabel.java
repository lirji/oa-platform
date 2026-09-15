package com.lrj.oa.iam.domain;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** 身份标签。可进入后续 ABAC Context，本期只作目录属性。 */
public enum IdentityLabel {
    EMPLOYEE,
    CONTRACTOR,
    ADMIN,
    PRIVILEGED,
    SERVICE_ACCOUNT,
    AGENT,
    EXTERNAL,
    HIGH_RISK;

    private static final Set<String> NAMES = Arrays.stream(values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    public static boolean isKnown(String raw) {
        return raw != null && NAMES.contains(raw);
    }
}
