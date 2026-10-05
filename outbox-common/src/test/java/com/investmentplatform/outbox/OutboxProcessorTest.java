package com.investmentplatform.outbox;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private OutboxStore<TestOutboxEvent> store;

    @Mock
    private OutboxPublisher publisher;

    private SimpleMeterRegistry registry;
    private OutboxProcessor<TestOutboxEvent> processor;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        OutboxProperties properties = new OutboxProperties(10, 2,
                new OutboxProperties.Backoff(Duration.ofSeconds(5), Duration.ofMinutes(1)), Duration.ofDays(7));
        processor = new OutboxProcessor<>("test", store, publisher, properties, CLOCK, registry);
    }

    @Test
    void publishBatch_publishesClaimedRowsAndMarksThemSent() {
        TestOutboxEvent a = TestOutboxEvent.create("a", NOW);
        TestOutboxEvent b = TestOutboxEvent.create("b", NOW);
        when(store.claimBatch(NOW, 10)).thenReturn(List.of(a, b));

        int sent = processor.publishBatch();

        assertThat(sent).isEqualTo(2);
        verify(publisher).publish("a", "{}");
        verify(publisher).publish("b", "{}");
        assertThat(a.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(b.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(registry.get("outbox.publish.success").tag("outbox", "test").counter().count()).isEqualTo(2);
    }

    @Test
    void publishBatch_onFailureSchedulesRetryAndStopsTheBatch() {
        TestOutboxEvent a = TestOutboxEvent.create("a", NOW);
        TestOutboxEvent b = TestOutboxEvent.create("b", NOW);
        when(store.claimBatch(NOW, 10)).thenReturn(List.of(a, b));
        doThrow(new IllegalStateException("broker down")).when(publisher).publish(eq("a"), any());

        int sent = processor.publishBatch();

        assertThat(sent).isZero();
        assertThat(a.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(a.getAttempts()).isEqualTo(1);
        assertThat(a.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(b.getStatus()).isEqualTo(OutboxStatus.PENDING);
        verify(publisher, never()).publish(eq("b"), any());
        assertThat(registry.get("outbox.publish.failure").tag("outbox", "test").counter().count()).isEqualTo(1);
    }

    @Test
    void publishBatch_marksRowFailedAfterMaxAttempts() {
        TestOutboxEvent a = TestOutboxEvent.create("a", NOW);
        when(store.claimBatch(NOW, 10)).thenReturn(List.of(a));
        doThrow(new IllegalStateException("poison")).when(publisher).publish(any(), any());

        processor.publishBatch();
        processor.publishBatch();

        assertThat(a.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(a.getAttempts()).isEqualTo(2);
    }

    @Test
    void pendingGauge_reflectsStoreCount() {
        when(store.countByStatus(OutboxStatus.PENDING)).thenReturn(7L);

        assertThat(registry.get("outbox.pending").tag("outbox", "test").gauge().value()).isEqualTo(7.0);
    }

    @Test
    void cleanup_deletesSentRowsOlderThanRetention() {
        when(store.deleteSentBefore(NOW.minusDays(7))).thenReturn(3);

        assertThat(processor.cleanup()).isEqualTo(3);
    }
}
