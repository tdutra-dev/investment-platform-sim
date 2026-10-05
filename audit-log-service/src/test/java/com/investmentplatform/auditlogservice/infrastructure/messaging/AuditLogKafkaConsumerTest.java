package com.investmentplatform.auditlogservice.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmentplatform.auditlogservice.infrastructure.persistence.AuditLogEntry;
import com.investmentplatform.auditlogservice.infrastructure.persistence.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.dao.DuplicateKeyException;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

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
    void onEvent_shouldExtractFieldsAndPersistEntry() {
        String payload = """
                {"eventType":"TransactionCreated","aggregateId":"agg-uuid-123",
                 "customerId":"cust-uuid-456","amount":1500.00,"currency":"EUR",
                 "type":"DEPOSIT","status":"PENDING","occurredAt":"2026-07-22T10:00:00"}
                """;

        consumer.onEvent(payload, "transaction-events");

        verify(auditLogRepository).insert(argThat((AuditLogEntry entry) ->
                "agg-uuid-123".equals(entry.getAggregateId()) &&
                "TransactionCreated".equals(entry.getEventType()) &&
                "transaction-events".equals(entry.getTopic()) &&
                entry.getPayload().contains("DEPOSIT") &&
                entry.getReceivedAt() != null
        ));
    }

    @Test
    void onEvent_shouldHandleMalformedJson_gracefully() {
        // Consumer must not throw — it logs a warning and saves with "unknown" fields
        assertThatCode(() -> consumer.onEvent("not-valid-json{{{", "transaction-events"))
                .doesNotThrowAnyException();

        verify(auditLogRepository).insert(argThat((AuditLogEntry entry) ->
                "unknown".equals(entry.getAggregateId()) &&
                "unknown".equals(entry.getEventType())
        ));
    }

    @Test
    void onEvent_shouldHandleMissingFields_gracefully() {
        // Valid JSON but missing aggregateId and eventType
        String payload = """
                {"amount":500.00,"currency":"USD"}
                """;

        consumer.onEvent(payload, "transaction-events");

        verify(auditLogRepository).insert(argThat((AuditLogEntry entry) ->
                "unknown".equals(entry.getAggregateId()) &&
                "unknown".equals(entry.getEventType())
        ));
    }

    @Test
    void onEvent_shouldUseEventIdAsDocumentId_andAcceptCustomerEvents() {
        String payload = "{\"eventId\":\"evt-1\",\"eventType\":\"CustomerRegistered\",\"aggregateId\":\"c-1\"}";

        consumer.onEvent(payload, "customer-events");

        verify(auditLogRepository).insert(argThat((AuditLogEntry entry) ->
                "evt-1".equals(entry.getId()) &&
                "customer-events".equals(entry.getTopic()) &&
                "CustomerRegistered".equals(entry.getEventType())));
    }

    @Test
    void onEvent_shouldIgnoreDuplicateDelivery() {
        String payload = "{\"eventId\":\"evt-2\",\"eventType\":\"TransactionCreated\",\"aggregateId\":\"a-1\"}";
        when(auditLogRepository.insert(any(AuditLogEntry.class)))
                .thenReturn(null)
                .thenThrow(new DuplicateKeyException("dup"));

        consumer.onEvent(payload, "transaction-events");
        assertThatCode(() -> consumer.onEvent(payload, "transaction-events")).doesNotThrowAnyException();

        verify(auditLogRepository, times(2)).insert(any(AuditLogEntry.class));
    }
}
