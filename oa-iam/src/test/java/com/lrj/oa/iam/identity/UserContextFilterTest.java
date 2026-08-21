package com.lrj.oa.iam.identity;

import com.lrj.oa.org.api.OrgQueryApi;
import com.lrj.oa.security.config.OaSecurityProperties;
import com.lrj.oa.security.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UserContextFilterTest {

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        UserContextHolder.clear();
    }

    @Test
    void jwtModeNeverTrustsDevIdentityHeader() throws Exception {
        OaSecurityProperties props = new OaSecurityProperties();
        props.setMode(OaSecurityProperties.Mode.JWT);
        UserContextFilter filter = new UserContextFilter(mock(OrgQueryApi.class), props);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws");
        request.addHeader(UserContextFilter.DEV_USER_HEADER, "victim-user");
        AtomicReference<String> observed = new AtomicReference<>("not-called");

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, resp) -> observed.set(UserContextHolder.peek() == null
                        ? null : UserContextHolder.peek().userId()));

        assertThat(observed.get()).isNull();
    }

    @Test
    void explicitDevModeStillSupportsSmokeIdentity() throws Exception {
        OaSecurityProperties props = new OaSecurityProperties();
        props.setMode(OaSecurityProperties.Mode.DEV);
        UserContextFilter filter = new UserContextFilter(mock(OrgQueryApi.class), props);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me/permissions");
        request.addHeader(UserContextFilter.DEV_USER_HEADER, "seed-user-1");
        AtomicReference<String> observed = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, resp) -> observed.set(UserContextHolder.require().userId()));

        assertThat(observed.get()).isEqualTo("seed-user-1");
        assertThat(UserContextHolder.peek()).isNull();
    }
}
