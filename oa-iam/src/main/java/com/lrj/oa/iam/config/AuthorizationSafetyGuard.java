package com.lrj.oa.iam.config;

import com.lrj.oa.security.config.OaSecurityProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 非本地身份模式下禁止关闭接口或数据权限强制执行。 */
@Component
public class AuthorizationSafetyGuard {

    public AuthorizationSafetyGuard(OaSecurityProperties security,
                                    @Value("${oa.iam.enforce:true}") boolean iamEnforce,
                                    @Value("${oa.iam.data-scope.strict:true}") boolean dataScopeStrict) {
        if (security.getMode() != OaSecurityProperties.Mode.JWT) return;
        if (!iamEnforce) {
            throw new IllegalStateException("JWT 模式禁止 oa.iam.enforce=false");
        }
        if (!dataScopeStrict) {
            throw new IllegalStateException("JWT 模式禁止 oa.iam.data-scope.strict=false");
        }
    }
}
