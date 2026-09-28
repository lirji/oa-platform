package com.lrj.oa.app;

import com.lrj.oa.common.web.GlobalExceptionHandler;
import com.lrj.oa.iam.aspect.IamWebMvcConfig;
import com.lrj.oa.iam.aspect.UnannotatedHandlerGuard;
import com.lrj.oa.org.api.IdentityDirectoryController;
import com.lrj.oa.org.application.directory.IdentityDirectoryExport;
import com.lrj.oa.org.infrastructure.directory.IdentityDirectorySecurityConfiguration;
import com.lrj.oa.security.config.OaSecurityConfig;
import com.lrj.oa.security.context.ServiceIdentity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 组装真实两个安全链和 IAM Handler 守卫，验证 DEV/员工身份也无法绕过专用服务认证。 */
class IdentityDirectoryHttpTest {
    private static final String KEY = "directory-test-key-" + "x".repeat(43);
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private IdentityDirectoryExport directory;

    @AfterEach void close() { if (context != null) { context.close(); } }

    @Test void disabledIsClosedEvenWhenOrdinarySecurityIsDev() throws Exception {
        open(false, false);
        mvc.perform(get("/internal/directory/v1/status")).andExpect(status().isNotFound());
        mvc.perform(authorized(get("/internal/directory/v1/status"))).andExpect(status().isNotFound());
        verifyNoInteractions(directory);
    }

    @Test void onlyFixedServiceCredentialWorksAndTenantHeadersCannotChangeScope() throws Exception {
        open(true, false);
        mvc.perform(get("/internal/directory/v1/status").secure(true)).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/directory/v1/status").secure(true).header("Authorization", "Bearer signed.employee.jwt")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/directory/v1/status").secure(true).header("Authorization", "Bearer " + "z".repeat(64))).andExpect(status().isUnauthorized());
        mvc.perform(authorized(get("/internal/directory/v1/status")).header("Authorization", "Bearer " + KEY)).andExpect(status().isUnauthorized());
        when(directory.status()).thenAnswer(invocation -> {
            assertThat(ServiceIdentity.require("oa-directory").tenantId()).isEqualTo(41);
            return new IdentityDirectoryExport.Status("oa", "test", "41", 8, 3, "a".repeat(64), 5);
        });
        mvc.perform(authorized(get("/internal/directory/v1/status")).header("X-Tenant-Id", "999").header("X-OA-User", "admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("source_tenant_ref").value("41"))
                .andExpect(jsonPath("last_sequence").value(8)).andExpect(jsonPath("backlog").value(5));
        assertThatThrownBy(() -> ServiceIdentity.require("oa-directory")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(directory, times(1)).status();
    }

    @Test void tlsAndLoopbackExceptionAreExplicitAndUntrustedForwardedHeadersDoNotHelp() throws Exception {
        open(true, false);
        mvc.perform(get("/internal/directory/v1/status").header("Authorization", "Bearer " + KEY)
                .header("X-Forwarded-Proto", "https")).andExpect(status().isForbidden());
        verifyNoInteractions(directory);
        context.close(); open(true, true);
        mvc.perform(get("/internal/directory/v1/status").header("Authorization", "Bearer " + KEY)
                .with(request -> { request.setRemoteAddr("127.0.0.1"); request.setLocalAddr("127.0.0.1"); return request; }))
                .andExpect(status().isOk());
        mvc.perform(get("/internal/directory/v1/status").header("Authorization", "Bearer " + KEY)
                .with(request -> { request.setRemoteAddr("10.1.2.3"); request.setLocalAddr("127.0.0.1"); return request; }))
                .andExpect(status().isForbidden());
    }

    @Test void namespaceAndHttpMethodsAreAllowlisted() throws Exception {
        open(true, false);
        mvc.perform(authorized(get("/internal/directory/v1/admin"))).andExpect(status().isForbidden());
        mvc.perform(authorized(post("/internal/directory/v1/events"))).andExpect(status().isForbidden());
        mvc.perform(authorized(get("/internal/directory/v1/ack"))).andExpect(status().isForbidden());
        mvc.perform(authorized(get("/internal/directory/v1/events")).param("after_sequence", "not-a-number"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(directory);
    }

    @Test void confirmationRejectsAmbiguousOrUnboundedJsonAndPreservesConflictStatus() throws Exception {
        open(true, false);
        String hash = "a".repeat(64);
        for (String invalid : List.of("{}", "[]", "{\"sequence\":1.5,\"fingerprint\":\"" + hash + "\"}",
                "{\"sequence\":\"1\",\"fingerprint\":\"" + hash + "\"}",
                "{\"sequence\":1,\"sequence\":2,\"fingerprint\":\"" + hash + "\"}",
                "{\"sequence\":1,\"fingerprint\":\"" + hash + "\",\"tenant\":42}",
                "{\"sequence\":1,\"fingerprint\":\"" + hash + "\"} {}")) {
            mvc.perform(authorized(post("/internal/directory/v1/ack")).content(invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(authorized(post("/internal/directory/v1/ack")).content("x".repeat(1025))).andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(directory);
        when(directory.acknowledge(1, hash)).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT));
        mvc.perform(authorized(post("/internal/directory/v1/ack")).content("{\"sequence\":1,\"fingerprint\":\"" + hash + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("error").value("directory_request_rejected"));
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.secure(true).header("Authorization", "Bearer " + KEY);
    }
    private void open(boolean enabled, boolean loopback) throws Exception {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        var values = new HashMap<String, Object>(); values.put("oa.security.mode", "DEV");
        values.put("oa.identity-directory.export.enabled", enabled);
        values.put("oa.identity-directory.export.tenant-id", 41);
        values.put("oa.identity-directory.export.source", "oa"); values.put("oa.identity-directory.export.environment", "test");
        values.put("oa.identity-directory.export.allow-loopback-http", loopback);
        values.put("oa.identity-directory.export.credential-sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(KEY.getBytes(StandardCharsets.US_ASCII))));
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("directory-test", values));
        context.register(WebConfig.class); context.refresh(); directory = context.getBean(IdentityDirectoryExport.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
    }
    @Configuration @EnableWebMvc
    @org.springframework.boot.context.properties.EnableConfigurationProperties
    @Import({OaSecurityConfig.class, IdentityDirectorySecurityConfiguration.class, IdentityDirectoryController.class,
            IamWebMvcConfig.class, UnannotatedHandlerGuard.class, GlobalExceptionHandler.class})
    static class WebConfig {
        @Bean IdentityDirectoryExport directory() { return mock(IdentityDirectoryExport.class); }
    }
}
