package com.lrj.oa.iam.config;

import com.lrj.oa.security.config.OaSecurityProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationSafetyGuardTest {

    @Test
    void jwt_requires_interface_and_data_enforcement() {
        OaSecurityProperties props = new OaSecurityProperties();

        assertThatThrownBy(() -> new AuthorizationSafetyGuard(props, false, true))
                .hasMessageContaining("oa.iam.enforce=false");
        assertThatThrownBy(() -> new AuthorizationSafetyGuard(props, true, false))
                .hasMessageContaining("data-scope.strict=false");
        assertThatCode(() -> new AuthorizationSafetyGuard(props, true, true)).doesNotThrowAnyException();
    }

    @Test
    void dev_can_use_report_mode_for_local_migration() {
        OaSecurityProperties props = new OaSecurityProperties();
        props.setMode(OaSecurityProperties.Mode.DEV);

        assertThatCode(() -> new AuthorizationSafetyGuard(props, false, false)).doesNotThrowAnyException();
    }
}
