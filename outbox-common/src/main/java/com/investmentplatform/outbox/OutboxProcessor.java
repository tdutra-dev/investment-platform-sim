package com.investmentplatform.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Claims a batch of pending rows, publishes them and records the outcome.
 * The caller must run {@link #publishBatch()} in a transaction: the row locks taken by the claim are held until
 * commit, which is what keeps several instances from publishing the same row.
 *
 * Metrics: {@code outbox.pending} (gauge), {@code outbox.publish.success} and {@code outbox.publish.failure} (counters),
 * all tagged with {@code outbox=<name>}.
 */
public class OutboxProcessor<E extends OutboxEventBase> {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final String name;
    private final OutboxStore<E> store;
    private final OutboxPublisher publisher;
    private final OutboxProperties properties;
    private final BackoffPolicy backoff;
    private final Clock clock;
    private final Counter successCounter;
    private final Counter failureCounter;

    public OutboxProcessor(String name, OutboxStore<E> store, OutboxPublisher publisher,
                           OutboxProperties properties, Clock clock, MeterRegistry registry) {
        this.name = name;
        this.store = store;
        this.publisher = publisher;
        this.properties = properties;
        this.backoff = properties.backoff().toPolicy();
        this.clock = clock;
        this.successCounter = Counter.builder("outbox.publish.success").tag("outbox", name).register(registry);
        this.failureCounter = Counter.builder("outbox.publish.failure").tag("outbox", name).register(registry);
        Gauge.builder("outbox.pending", store, s -> s.countByStatus(OutboxStatus.PENDING))
                .tag("outbox", name).register(registry);
    }

    /** @return number of rows published in this run */
    public int publishBatch() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<E> batch = store.claimBatch(now, properties.batchSize());
        int sent = 0;
        for (E event : batch) {
            MDC.put("eventId", event.getId().toString());
            try {
                publisher.publish(event.getAggregateId(), event.getPayload());
                event.markSent(LocalDateTime.now(clock));
                successCounter.increment();
                sent++;
                log.debug("Outbox[{}]: sent {} aggregateId={}", name, event.getEventType(), event.getAggregateId());
            } catch (RuntimeException e) {
                event.markAttemptFailed(e.getMessage(), now, backoff, properties.maxAttempts());
                failureCounter.increment();
                log.warn("Outbox[{}]: publish failed for {} aggregateId={} attempt={} status={}: {}",
                        name, event.getEventType(), event.getAggregateId(), event.getAttempts(),
                        event.getStatus(), e.getMessage());
                // Likely a broker problem: stop the batch. The failed row waits for its backoff; others go on next run.
                break;
            } finally {
                MDC.remove("eventId");
            }
        }
        return sent;
    }

    /** Deletes SENT rows older than the retention period. */
    public int cleanup() {
        int deleted = store.deleteSentBefore(LocalDateTime.now(clock).minus(properties.retention()));
        if (deleted > 0) {
            log.info("Outbox[{}]: deleted {} sent rows older than {}", name, deleted, properties.retention());
        }
        return deleted;
    }
}
