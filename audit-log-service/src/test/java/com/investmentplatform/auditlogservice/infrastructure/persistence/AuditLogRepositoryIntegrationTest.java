package com.investmentplatform.auditlogservice.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test — verifies MongoDB persistence with a real MongoDB 7 container.
 * Uses @DataMongoTest slice (loads only MongoDB context, not Kafka).
 * Requires Docker. Skipped automatically if Docker is unavailable.
 */
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
class AuditLogRepositoryIntegrationTest {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void cleanUp() {
        auditLogRepository.deleteAll();
    }

    @Test
    void save_shouldPersistAllFieldsCorrectly() {
        String payload = "{\"eventType\":\"TransactionCreated\",\"aggregateId\":\"test-agg\"}";
        AuditLogEntry entry = AuditLogEntry.create(
                "test-agg", "TransactionCreated", "transaction-events", payload);

        AuditLogEntry saved = auditLogRepository.save(entry);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAggregateId()).isEqualTo("test-agg");
        assertThat(saved.getEventType()).isEqualTo("TransactionCreated");
        assertThat(saved.getTopic()).isEqualTo("transaction-events");
        assertThat(saved.getPayload()).isEqualTo(payload);
        assertThat(saved.getReceivedAt()).isNotNull();
    }

    @Test
    void findByAggregateId_shouldReturnOnlyMatchingEntries() {
        auditLogRepository.save(AuditLogEntry.create("agg-1", "TransactionCreated",   "transaction-events", "{}"));
        auditLogRepository.save(AuditLogEntry.create("agg-1", "TransactionCompleted", "transaction-events", "{}"));
        auditLogRepository.save(AuditLogEntry.create("agg-2", "TransactionCreated",   "transaction-events", "{}"));

        List<AuditLogEntry> results =
                auditLogRepository.findByAggregateIdOrderByReceivedAtDesc("agg-1");

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(e -> "agg-1".equals(e.getAggregateId()));
        // Both event types present
        assertThat(results).extracting(AuditLogEntry::getEventType)
                .containsExactlyInAnyOrder("TransactionCreated", "TransactionCompleted");
    }

    @Test
    void findByAggregateId_shouldReturnEmpty_whenNoMatchingEntries() {
        List<AuditLogEntry> results =
                auditLogRepository.findByAggregateIdOrderByReceivedAtDesc("nonexistent");

        assertThat(results).isEmpty();
    }
}
