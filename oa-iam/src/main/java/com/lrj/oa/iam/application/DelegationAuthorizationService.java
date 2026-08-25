package com.lrj.oa.iam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.iam.api.DelegationRule;
import com.lrj.oa.iam.domain.Delegation;
import com.lrj.oa.iam.infrastructure.mapper.DelegationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 委托授权事实的唯一服务端判定入口。非法历史数据按 fail-closed 处理。 */
@Service
public class DelegationAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(DelegationAuthorizationService.class);
    private final DelegationMapper mapper;
    private final ObjectMapper json;

    public DelegationAuthorizationService(DelegationMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    public List<DelegationRule> activeRules(String delegateeUserId) {
        return mapper.selectActiveForDelegatee(TenantContext.get(), delegateeUserId).stream()
                .map(this::toRule)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public boolean canActOnBehalf(String delegateeUserId, String delegatorUserId,
                                  String processDefinitionKey, String candidateGroup) {
        return activeRules(delegateeUserId).stream()
                .anyMatch(r -> r.delegatorUserId().equals(delegatorUserId)
                        && r.matches(processDefinitionKey, candidateGroup));
    }

    private DelegationRule toRule(Delegation d) {
        String scope = d.getScope();
        if (!Set.of("ALL_TODO", "BY_PROCESS_KEY", "BY_ROLE").contains(scope)) {
            log.warn("忽略非法委托 scope: id={} scope={}", d.getId(), scope);
            return null;
        }
        Set<String> processKeys = readSet(d.getProcessKeys(), String[].class);
        Set<Long> roleIds = readSet(d.getRoleIds(), Long[].class);
        if ("BY_PROCESS_KEY".equals(scope) && processKeys.isEmpty()) return null;
        if ("BY_ROLE".equals(scope) && roleIds.isEmpty()) return null;
        return new DelegationRule(d.getId(), d.getDelegatorUserId(), scope, processKeys, roleIds);
    }

    private <T> Set<T> readSet(String value, Class<T[]> type) {
        if (value == null || value.isBlank()) return Set.of();
        try {
            return Set.copyOf(new LinkedHashSet<>(Arrays.asList(json.readValue(value, type))));
        } catch (Exception e) {
            log.warn("忽略不可解析的委托范围: {}", value);
            return Set.of();
        }
    }
}
