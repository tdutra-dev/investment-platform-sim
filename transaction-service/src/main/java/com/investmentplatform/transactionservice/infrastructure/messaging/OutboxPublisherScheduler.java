package com.investmentplatform.transactionservice.infrastructure.messaging;

import com.investmentplatform.outbox.OutboxProcessor;
import com.investmentplatform.transactionservice.infrastructure.persistence.OutboxEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triggers the shared {@link OutboxProcessor}. Each run is one transaction: rows are claimed with
 * {@code FOR UPDATE SKIP LOCKED}, published, marked, and the locks are released on commit.
 * Scheduling is switched off in tests via {@code scheduling.enabled=false} (see SchedulingConfig).
 */
@Component
public class OutboxPublisherScheduler {

    private final OutboxProcessor<OutboxEvent> processor;

    public OutboxPublisherScheduler(OutboxProcessor<OutboxEvent> processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        processor.publishBatch();
    }

    @Scheduled(fixedDelayString = "${outbox.cleanup.fixed-delay-ms:3600000}")
    @Transactional
    public void cleanupSentEvents() {
        processor.cleanup();
    }
}
