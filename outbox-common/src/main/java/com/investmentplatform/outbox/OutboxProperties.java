package com.investmentplatform.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Outbox settings, e.g. {@code outbox.batch-size=50}, {@code outbox.max-attempts=5},
 * {@code outbox.backoff.initial=1s}, {@code outbox.backoff.max=5m}, {@code outbox.retention=7d}.
 * The scheduler delays are {@code outbox.publisher.fixed-delay-ms} and {@code outbox.cleanup.fixed-delay-ms}.
 */
@ConfigurationProperties("outbox")
public record OutboxProperties(
        @DefaultValue("50") int batchSize,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue Backoff backoff,
        @DefaultValue("7d") Duration retention) {

    public record Backoff(@DefaultValue("1s") Duration initial, @DefaultValue("5m") Duration max) {
        public BackoffPolicy toPolicy() {
            return new BackoffPolicy(initial, max);
        }
    }
}
