package com.investmentplatform.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventBaseTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 12, 0);
    private final BackoffPolicy backoff = new BackoffPolicy(Duration.ofSeconds(10), Duration.ofMinutes(5));

    @Test
    void newEventIsPendingAndImmediatelyDue() {
        TestOutboxEvent event = TestOutboxEvent.create("agg", NOW);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void markSent_movesToSentAndClearsError() {
        TestOutboxEvent event = TestOutboxEvent.create("agg", NOW);
        event.markAttemptFailed("boom", NOW, backoff, 5);

        event.markSent(NOW.plusSeconds(30));

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(event.getSentAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void failedAttempt_staysPendingAndSchedulesRetryWithBackoff() {
        TestOutboxEvent event = TestOutboxEvent.create("agg", NOW);

        event.markAttemptFailed("broker down", NOW, backoff, 3);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("broker down");
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(10));

        event.markAttemptFailed("broker down", NOW, backoff, 3);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(20));
    }

    @Test
    void reachingMaxAttempts_movesToFailed() {
        TestOutboxEvent event = TestOutboxEvent.create("agg", NOW);

        event.markAttemptFailed("e1", NOW, backoff, 2);
        event.markAttemptFailed("e2", NOW, backoff, 2);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(2);
        assertThat(event.getLastError()).isEqualTo("e2");
    }

    @Test
    void longErrorMessagesAreTruncated() {
        TestOutboxEvent event = TestOutboxEvent.create("agg", NOW);

        event.markAttemptFailed("x".repeat(5000), NOW, backoff, 5);

        assertThat(event.getLastError()).hasSize(1000);
    }
}
