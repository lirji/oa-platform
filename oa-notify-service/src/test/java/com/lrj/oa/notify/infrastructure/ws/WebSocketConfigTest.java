package com.lrj.oa.notify.infrastructure.ws;

import com.lrj.oa.security.config.OaSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketConfigTest {

    @Test
    void jwtModeConsumesOneTimeTicketAndIgnoresUserId() {
        OaSecurityProperties props = new OaSecurityProperties();
        WsTicketService tickets = mock(WsTicketService.class);
        when(tickets.consume("ticket-value"))
                .thenReturn(Optional.of(new WsTicketService.TicketIdentity("real-user", 1L)));
        var interceptor = new WebSocketConfig.IdentityHandshakeInterceptor(props, tickets);
        MockHttpServletRequest servlet = new MockHttpServletRequest("GET", "/ws");
        servlet.addParameter("ticket", "ticket-value");
        servlet.addParameter("userId", "victim-user");
        var attributes = new HashMap<String, Object>();

        boolean accepted = interceptor.beforeHandshake(new ServletServerHttpRequest(servlet),
                mock(org.springframework.http.server.ServerHttpResponse.class), mock(WebSocketHandler.class), attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes.get(NotifyWebSocketHandler.ATTR_USER)).isEqualTo("real-user");
        verify(tickets).consume("ticket-value");
    }

    @Test
    void jwtModeRejectsReplayOrDevIdentityParameter() {
        OaSecurityProperties props = new OaSecurityProperties();
        WsTicketService tickets = mock(WsTicketService.class);
        when(tickets.consume(null)).thenReturn(Optional.empty());
        var interceptor = new WebSocketConfig.IdentityHandshakeInterceptor(props, tickets);
        MockHttpServletRequest servlet = new MockHttpServletRequest("GET", "/ws");
        servlet.addParameter("userId", "victim-user");

        assertThat(interceptor.beforeHandshake(new ServletServerHttpRequest(servlet),
                mock(org.springframework.http.server.ServerHttpResponse.class), mock(WebSocketHandler.class),
                new HashMap<>())).isFalse();
    }

    @Test
    void explicitDevModeKeepsExistingProbeContract() {
        OaSecurityProperties props = new OaSecurityProperties();
        props.setMode(OaSecurityProperties.Mode.DEV);
        var interceptor = new WebSocketConfig.IdentityHandshakeInterceptor(props, mock(WsTicketService.class));
        MockHttpServletRequest servlet = new MockHttpServletRequest("GET", "/ws");
        servlet.addParameter("userId", "seed-user-13");
        var attributes = new HashMap<String, Object>();

        assertThat(interceptor.beforeHandshake(new ServletServerHttpRequest(servlet),
                mock(org.springframework.http.server.ServerHttpResponse.class), mock(WebSocketHandler.class),
                attributes)).isTrue();
        assertThat(attributes.get(NotifyWebSocketHandler.ATTR_USER)).isEqualTo("seed-user-13");
    }
}
