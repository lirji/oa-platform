package com.lrj.oa.security.context;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;

/** 仅由专用服务认证链构造；企业范围来自部署配置，不能从请求租户头或员工 JWT 复制。 */
public record ServiceIdentity(String service, long tenantId) {
    public ServiceIdentity {
        if (service == null || service.isBlank() || tenantId < 1) { throw new IllegalArgumentException("服务身份无效"); }
    }
    /** Handler 的执行边界二次校验，避免错误的认证链配置把普通登录身份放行。 */
    public static ServiceIdentity require(String expected) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof ServiceIdentity identity)
                || !identity.service().equals(expected)) { throw new AccessDeniedException("服务身份不匹配"); }
        return identity;
    }
}
