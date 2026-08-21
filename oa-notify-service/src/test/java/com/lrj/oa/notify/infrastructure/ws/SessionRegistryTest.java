package com.lrj.oa.notify.infrastructure.ws;

import com.lrj.oa.notify.NotifyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SessionRegistryTest {

    @Test
    void concurrentHandshakesCannotExceedConfiguredLimit() throws Exception {
        NotifyProperties props = new NotifyProperties();
        props.getWs().setMaxSessions(8);
        SessionRegistry registry = new SessionRegistry(props);
        int attempts = 24;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();

        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < attempts; i++) {
                int index = i;
                pool.submit(() -> {
                    start.await();
                    if (registry.register("user-" + index, mock(WebSocketSession.class))) accepted.incrementAndGet();
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(accepted).hasValue(8);
        assertThat(registry.stats()).containsEntry("sessions", 8L).containsEntry("users", 8);
    }
}
