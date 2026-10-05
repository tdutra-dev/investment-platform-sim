package com.investmentplatform.transactionservice.infrastructure.messaging;

import com.investmentplatform.transactionservice.infrastructure.persistence.OutboxEvent;
import com.investmentplatform.transactionservice.infrastructure.persistence.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherSchedulerTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaEventProducer kafkaEventProducer;

    @InjectMocks
    private OutboxPublisherScheduler scheduler;

    @Test
    void publishPendingEvents_shouldPublishAndMarkAsSent() {
        OutboxEvent event = OutboxEvent.create(UUID.randomUUID(), "agg-1", "TransactionCreated", "{}");
        when(outboxRepository.findByPublishedFalse()).thenReturn(List.of(event));

        scheduler.publishPendingEvents();

        verify(kafkaEventProducer).publish("agg-1", "{}");
        assertThat(event.isPublished()).isTrue();
        verify(outboxRepository).save(event);
    }

    @Test
    void publishPendingEvents_shouldKeepRowPendingWhenKafkaFails() {
        OutboxEvent event = OutboxEvent.create(UUID.randomUUID(), "agg-1", "TransactionCreated", "{}");
        when(outboxRepository.findByPublishedFalse()).thenReturn(List.of(event));
        doThrow(new IllegalStateException("broker down")).when(kafkaEventProducer).publish(any(), any());

        scheduler.publishPendingEvents();

        assertThat(event.isPublished()).isFalse();
        verify(outboxRepository, never()).save(any());
    }
}
