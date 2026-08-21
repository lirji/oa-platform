package com.lrj.oa.iam.domain;

import java.util.List;

/** 一条独立授权来源的条件分支；分支内 AND，分支之间 OR。 */
public record AbacBranch(long roleId, List<Condition> conditions) {
    public AbacBranch {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    public record Condition(long id, String expression, String description) {}
}
