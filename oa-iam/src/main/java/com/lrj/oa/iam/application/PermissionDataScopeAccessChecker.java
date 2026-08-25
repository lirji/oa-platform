package com.lrj.oa.iam.application;

import com.lrj.oa.security.context.UserContextHolder;
import com.lrj.oa.security.model.DataScopeRule;
import com.lrj.oa.security.port.DataScopeAccessChecker;
import com.lrj.oa.security.port.PermissionChecker;
import com.lrj.oa.iam.aspect.AuthorizationContext;
import org.springframework.stereotype.Component;

/** 权限快照驱动的对象级范围检查，不访问数据库。 */
@Component
public class PermissionDataScopeAccessChecker implements DataScopeAccessChecker {

    private final PermissionChecker permissions;

    public PermissionDataScopeAccessChecker(PermissionChecker permissions) {
        this.permissions = permissions;
    }

    @Override
    public boolean allows(String permissionCode, Long orgId, String orgPath, String ownerUserId) {
        String userId = UserContextHolder.require().userId();
        DataScopeRule rule = AuthorizationContext.effectiveScope(permissionCode)
                .orElseGet(() -> permissions.dataScopeForPermission(userId, permissionCode));
        return switch (rule.type()) {
            case ALL -> true;
            case NONE -> false;
            case SELF -> userId.equals(ownerUserId);
            case ORG -> orgId != null && rule.orgIds().contains(orgId);
            case ORG_AND_SUB, CUSTOM -> {
                boolean exact = orgId != null && rule.orgIds().contains(orgId);
                boolean descendant = orgPath != null && rule.pathPrefixes().stream().anyMatch(orgPath::startsWith);
                boolean self = rule.type() == com.lrj.oa.security.model.DataScopeType.CUSTOM
                        && ownerUserId != null && userId.equals(ownerUserId)
                        && rule.selfUserId() != null;
                yield exact || descendant || self;
            }
        };
    }
}
