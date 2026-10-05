package com.investmentplatform.auditlogservice.infrastructure.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

/**
 * MongoDB document representing a persisted domain event received from Kafka.
 * One document per event consumed — full payload stored verbatim.
 */
@Document(collection = "audit_log")
public class AuditLogEntry {

    @Id
    private String id;

    @Indexed
    private String aggregateId;

    private String eventType;

    private String topic;

    private String payload;

    private LocalDateTime receivedAt;

    protected AuditLogEntry() {
    }

    public static AuditLogEntry create(String aggregateId, String eventType,
                                       String topic, String payload) {
        return create(null, aggregateId, eventType, topic, payload);
    }

    /** The document id is the event id, so a redelivered event maps to the same document. */
    public static AuditLogEntry create(String eventId, String aggregateId, String eventType,
                                       String topic, String payload) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.id = eventId;
        entry.aggregateId = aggregateId;
        entry.eventType = eventType;
        entry.topic = topic;
        entry.payload = payload;
        entry.receivedAt = LocalDateTime.now();
        return entry;
    }

    public String getId() {
        return id;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }
}
