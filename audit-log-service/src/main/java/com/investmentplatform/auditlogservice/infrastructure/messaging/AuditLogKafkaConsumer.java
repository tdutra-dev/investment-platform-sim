package com.investmentplatform.auditlogservice.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmentplatform.auditlogservice.infrastructure.persistence.AuditLogEntry;
import com.investmentplatform.auditlogservice.infrastructure.persistence.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer — listens to all event topics and persists each message
 * as an {@link AuditLogEntry} document in MongoDB.
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

    @KafkaListener(topics = "transaction-events")
    public void onTransactionEvent(String payload,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.debug("Received event from topic={}", topic);

        String aggregateId = extractField(payload, "aggregateId");
        String eventType   = extractField(payload, "eventType");

        AuditLogEntry entry = AuditLogEntry.create(aggregateId, eventType, topic, payload);
        auditLogRepository.save(entry);

        log.info("Audit log persisted: eventType={} aggregateId={}", eventType, aggregateId);
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
