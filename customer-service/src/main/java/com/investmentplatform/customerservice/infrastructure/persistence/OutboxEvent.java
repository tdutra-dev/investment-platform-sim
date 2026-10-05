package com.investmentplatform.customerservice.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Technical entity for the Outbox Pattern.
 *
 * Written in the SAME DB transaction as {@link com.investmentplatform.customerservice.domain.Customer},
 * guaranteeing that if the transaction is persisted, the event is also persisted —
 * eliminating the dual-write inconsistency between DB and Kafka.
 *
 * The {@link com.investmentplatform.customerservice.infrastructure.messaging.OutboxPublisherScheduler}
 * reads rows where {@code published = false} and publishes them to Kafka.
 */
@Entity
@Table(name = "customer_customer_outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, length = 4096)
    private String payload;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private boolean published;

    protected OutboxEvent() {
    }

    public static OutboxEvent create(UUID id, String aggregateId, String eventType, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.id = id;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.payload = payload;
        event.createdAt = LocalDateTime.now();
        event.published = false;
        return event;
    }

    public void markPublished() {
        this.published = true;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isPublished() {
        return published;
    }
}
