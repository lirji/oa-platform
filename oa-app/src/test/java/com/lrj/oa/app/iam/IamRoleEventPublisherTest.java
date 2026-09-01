package com.lrj.oa.app.iam;

import com.lrj.oa.iam.api.IamRoleEventOutboxApi;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

class IamRoleEventPublisherTest {

    @Test
    @SuppressWarnings("unchecked")
    void successfulSendMarksLeasedEventSent() {
        IamRoleEventOutboxApi outbox = mock(IamRoleEventOutboxApi.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        ObjectProvider<KafkaTemplate<String, String>> kafkaProvider = mock(ObjectProvider.class);
        ObjectProvider<MeterRegistry> meters = mock(ObjectProvider.class);
        when(kafkaProvider.getIfAvailable()).thenReturn(kafka);
        when(meters.getIfAvailable()).thenReturn(null);
        var event = new IamRoleEventOutboxApi.PendingEvent(
                3L, "e1", 1L, "iam.role.changed.v1", "1:19", "{}", 0);
        when(outbox.claimPending(eq(1), anyString())).thenReturn(List.of(event), List.of());
        when(kafka.send(event.topic(), event.messageKey(), event.payload()))
                .thenReturn(CompletableFuture.completedFuture(null));

        IamRoleEventPublisher publisher = new IamRoleEventPublisher(outbox, kafkaProvider, meters, 10);
        publisher.publishPending();

        verify(kafka).send(event.topic(), event.messageKey(), event.payload());
        verify(outbox).markSent(eq(3L), anyString());
        verify(outbox, never()).markFailed(anyLong(), anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedSendReturnsEventToRetryState() {
        IamRoleEventOutboxApi outbox = mock(IamRoleEventOutboxApi.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        ObjectProvider<KafkaTemplate<String, String>> kafkaProvider = mock(ObjectProvider.class);
        ObjectProvider<MeterRegistry> meters = mock(ObjectProvider.class);
        when(kafkaProvider.getIfAvailable()).thenReturn(kafka);
        when(meters.getIfAvailable()).thenReturn(null);
        var event = new IamRoleEventOutboxApi.PendingEvent(
                4L, "e2", 1L, "iam.role.changed.v1", "1:20", "{}", 0);
        when(outbox.claimPending(eq(1), anyString())).thenReturn(List.of(event), List.of());
        when(kafka.send(event.topic(), event.messageKey(), event.payload()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        IamRoleEventPublisher publisher = new IamRoleEventPublisher(outbox, kafkaProvider, meters, 10);
        publisher.publishPending();

        verify(outbox).markFailed(eq(4L), anyString(), contains("broker down"));
        verify(outbox, never()).markSent(anyLong(), anyString());
    }
}
