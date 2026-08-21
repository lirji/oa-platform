package com.lrj.oa.notify.infrastructure.ws;

import com.lrj.oa.notify.NotifyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WsTicketServiceTest {

    @Test
    void ticketIsUrlSafeShortLivedAndConsumedOnce() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(30)))).thenReturn(true);
        WsTicketService service = new WsTicketService(redis, new NotifyProperties());

        WsTicketService.IssuedTicket issued = service.issue("casdoor-sub-1", 1L);
        assertThat(issued.ticket()).matches("[A-Za-z0-9_-]{43}");
        assertThat(issued.expiresInSeconds()).isEqualTo(30);
        verify(values).setIfAbsent(eq(WsTicketService.KEY_PREFIX + issued.ticket()), anyString(),
                eq(Duration.ofSeconds(30)));

        when(values.getAndDelete(WsTicketService.KEY_PREFIX + issued.ticket()))
                .thenReturn("1.Y2FzZG9vci1zdWItMQ")
                .thenReturn((String) null);
        assertThat(service.consume(issued.ticket())).contains(new WsTicketService.TicketIdentity("casdoor-sub-1", 1L));
        assertThat(service.consume(issued.ticket())).isEmpty();
    }

    @Test
    void rejectsUnsafeTtlAndMalformedTicket() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        NotifyProperties props = new NotifyProperties();
        props.getWs().setTicketTtlSeconds(300);
        assertThatThrownBy(() -> new WsTicketService(redis, props)).isInstanceOf(IllegalArgumentException.class);

        props.getWs().setTicketTtlSeconds(30);
        WsTicketService service = new WsTicketService(redis, props);
        assertThat(service.consume("access-token-shaped.value")).isEmpty();
    }
}
