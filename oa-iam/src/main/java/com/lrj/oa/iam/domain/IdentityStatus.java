package com.lrj.oa.iam.domain;

import java.util.Set;

/** 身份生命周期。Human 的 DISABLED 由离职投影驱动，不替代员工主数据。 */
public enum IdentityStatus {
    CREATED, ACTIVE, SUSPENDED, DISABLED, EXPIRED, DELETED;

    public boolean visible() {
        return this != DELETED;
    }

    public boolean canTransitTo(IdentityStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case CREATED -> target == ACTIVE || target == DISABLED;
            case ACTIVE -> target == SUSPENDED || target == DISABLED || target == EXPIRED;
            case SUSPENDED -> target == ACTIVE || target == DISABLED || target == EXPIRED;
            case DISABLED -> target == ACTIVE || target == DELETED;
            case EXPIRED -> target == DISABLED || target == DELETED;
            case DELETED -> false;
        };
    }

    public static Set<String> visibleNames() {
        return Set.of(CREATED.name(), ACTIVE.name(), SUSPENDED.name(), DISABLED.name(), EXPIRED.name());
    }
}
