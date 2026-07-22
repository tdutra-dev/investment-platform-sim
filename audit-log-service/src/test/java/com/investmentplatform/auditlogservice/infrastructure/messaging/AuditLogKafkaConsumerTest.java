package com.investmentplatform.auditlogservice.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmentplatform.auditlogservice.infrastructure.persistence.AuditLogEntry;
import com.investmentplatform.auditlogservice.infrastructure.persistence.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogKafkaConsumerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AuditLogKafkaConsumer(auditLogRepository, new ObjectMapper());
    }

    @Test
    void onTransactionEvent_shouldExtractFieldsAndPersistEntry() {
        String payload = """
                {"eventType":"TransactionCreated","aggregateId":"agg-uuid-123",
                 "customerId":"cust-uuid-456","amount":1500.00,"currency":"EUR",
                 "type":"DEPOSIT","status":"PENDING","occurredAt":"2026-07-22T10:00:00"}
                """;

        consumer.onTransactionEvent(payload, "transaction-events");

        verify(auditLogRepository).save(argThat(entry ->
                "agg-uuid-123".equals(entry.getAggregateId()) &&
                "TransactionCreated".equals(entry.getEventType()) &&
                "transaction-events".equals(entry.getTopic()) &&
                entry.getPayload().contains("DEPOSIT") &&
                entry.getReceivedAt() != null
        ));
    }

    @Test
    void onTransactionEvent_shouldHandleMalformedJson_gracefully() {
        // Consumer must not throw — it logs a warning and saves with "unknown" fields
        assertThatCode(() -> consumer.onTransactionEvent("not-valid-json{{{", "transaction-events"))
                .doesNotThrowAnyException();

        verify(auditLogRepository).save(argThat(entry ->
                "unknown".equals(entry.getAggregateId()) &&
                "unknown".equals(entry.getEventType())
        ));
    }

    @Test
    void onTransactionEvent_shouldHandleMissingFields_gracefully() {
        // Valid JSON but missing aggregateId and eventType
        String payload = """
                {"amount":500.00,"currency":"USD"}
                """;

        consumer.onTransactionEvent(payload, "transaction-events");

        verify(auditLogRepository).save(argThat(entry ->
                "unknown".equals(entry.getAggregateId()) &&
                "unknown".equals(entry.getEventType())
        ));
    }
}
