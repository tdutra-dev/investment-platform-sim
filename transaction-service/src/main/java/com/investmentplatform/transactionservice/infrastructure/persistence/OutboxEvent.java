package com.investmentplatform.transactionservice.infrastructure.persistence;

import com.investmentplatform.outbox.OutboxEventBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbox row of the transaction service. Written in the SAME DB transaction as the business change,
 * published to Kafka later by {@link com.investmentplatform.transactionservice.infrastructure.messaging.OutboxPublisherScheduler}.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent extends OutboxEventBase {

    protected OutboxEvent() {
    }

    public static OutboxEvent create(UUID id, String aggregateId, String eventType, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.init(id, aggregateId, eventType, payload, LocalDateTime.now());
        return event;
    }
}
