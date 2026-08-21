package com.lrj.oa.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OaSecurityConfigTest {

    @Test
    void productionSafeDefaultIsJwt() {
        OaSecurityProperties props = new OaSecurityProperties();
        assertThat(props.getMode()).isEqualTo(OaSecurityProperties.Mode.JWT);
        assertThat(props.getPublicPaths()).contains(
                "/api/v1/system/ping", "/api/v1/notify/ping", "/api/v1/file/ping", "/api/v1/job/ping");
    }

    @Test
    void audienceMustContainConfiguredClient() {
        var validator = OaSecurityConfig.audienceValidator("oa-platform-local");
        Jwt accepted = jwt(List.of("oa-platform-local", "another"));
        Jwt rejected = jwt(List.of("another"));

        assertThat(validator.validate(accepted).hasErrors()).isFalse();
        assertThat(validator.validate(rejected).hasErrors()).isTrue();
    }

    private static Jwt jwt(List<String> audience) {
        return Jwt.withTokenValue("test")
                .header("alg", "RS256")
                .subject("user-1")
                .audience(audience)
                .build();
    }
}
