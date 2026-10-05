package com.investmentplatform.auditlogservice.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmentplatform.auditlogservice.infrastructure.persistence.AuditLogEntry;
import com.investmentplatform.auditlogservice.infrastructure.persistence.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Kafka consumer — listens to transaction-events and customer-events and persists each
 * message as an {@link AuditLogEntry} document in MongoDB. Idempotent: delivery is
 * at-least-once, so duplicates are dropped using the eventId as document id.
 *
 * Intentionally no domain logic here: the audit-log service is a pure
 * observer that stores the raw event payload for observability.
 */
@Component
public class AuditLogKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditLogKafkaConsumer.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogKafkaConsumer(AuditLogRepository auditLogRepository,
                                  ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {"transaction-events", "customer-events"})
    public void onEvent(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.debug("Received event from topic={}", topic);

        String aggregateId = extractField(payload, "aggregateId");
        String eventType   = extractField(payload, "eventType");
        String eventId     = resolveEventId(payload, topic);

        try {
            // insert (not save): a second delivery of the same eventId fails on the _id and is skipped
            auditLogRepository.insert(AuditLogEntry.create(eventId, aggregateId, eventType, topic, payload));
            log.info("Audit log persisted: eventType={} aggregateId={}", eventType, aggregateId);
        } catch (DuplicateKeyException e) {
            log.info("Duplicate event ignored: eventId={} eventType={}", eventId, eventType);
        }
    }

    /** Uses the producer-assigned eventId; falls back to a content hash for events without one. */
    private String resolveEventId(String payload, String topic) {
        String eventId = extractField(payload, "eventId");
        if (!"unknown".equals(eventId) && !eventId.isBlank()) {
            return eventId;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((topic + ":" + payload).getBytes(StandardCharsets.UTF_8));
            return "sha256-" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String extractField(String json, String field) {
        try {
            return objectMapper.readTree(json).path(field).asText("unknown");
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("Could not extract '{}' from payload: {}", field, e.getMessage());
            return "unknown";
        }
    }
}
