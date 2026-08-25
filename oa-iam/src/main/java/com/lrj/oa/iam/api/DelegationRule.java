package com.lrj.oa.iam.api;

import java.util.Set;

/** 供流程域消费的、已校验且当前生效的委托规则。 */
public record DelegationRule(
        long id,
        String delegatorUserId,
        String scope,
        Set<String> processKeys,
        Set<Long> roleIds
) {
    public boolean matches(String processDefinitionKey, String candidateGroup) {
        return switch (scope) {
            case "ALL_TODO" -> true;
            case "BY_PROCESS_KEY" -> processDefinitionKey != null && processKeys.contains(processDefinitionKey);
            case "BY_ROLE" -> candidateRoleId(candidateGroup) != null
                    && roleIds.contains(candidateRoleId(candidateGroup));
            default -> false;
        };
    }

    private static Long candidateRoleId(String candidateGroup) {
        if (candidateGroup == null || candidateGroup.isBlank()) return null;
        String value = candidateGroup.startsWith("role:") ? candidateGroup.substring(5) : candidateGroup;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
