package com.investmentplatform.transactionservice.infrastructure.messaging;

import com.investmentplatform.transactionservice.infrastructure.persistence.OutboxEvent;
import com.investmentplatform.transactionservice.infrastructure.persistence.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox Pattern — scheduled publisher.
 *
 * Every {@code fixedDelay} ms this job:
 * 1. Reads all rows from {@code outbox_events} where {@code published = false}
 * 2. Publishes each payload to Kafka via {@link KafkaEventProducer}
 * 3. Marks each row as published and saves
 *
 * Enabled only when {@code scheduling.enabled=true} (see SchedulingConfig).
 * Disabled in tests via {@code scheduling.enabled=false} in test application.properties.
 */
@Component
public class OutboxPublisherScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherScheduler.class);

    private final OutboxRepository outboxRepository;
    private final KafkaEventProducer kafkaEventProducer;

    public OutboxPublisherScheduler(OutboxRepository outboxRepository,
                                    KafkaEventProducer kafkaEventProducer) {
        this.outboxRepository = outboxRepository;
        this.kafkaEventProducer = kafkaEventProducer;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> unpublished = outboxRepository.findByPublishedFalse();
        if (unpublished.isEmpty()) {
            return;
        }

        log.debug("Outbox: processing {} unpublished events", unpublished.size());

        for (OutboxEvent event : unpublished) {
            try {
                kafkaEventProducer.publish(event.getAggregateId(), event.getPayload());
                event.markPublished();
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Outbox: failed to publish event {} (aggregateId={}): {}",
                        event.getEventType(), event.getAggregateId(), e.getMessage());
            }
        }
    }
}
