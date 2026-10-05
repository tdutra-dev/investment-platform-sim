package com.investmentplatform.transactionservice.infrastructure.messaging;

import com.investmentplatform.transactionservice.application.TransactionService;
import com.investmentplatform.transactionservice.domain.TransactionType;
import com.investmentplatform.transactionservice.infrastructure.persistence.OutboxRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test — verifies the Outbox Pattern round-trip:
 * Transaction + OutboxEvent written atomically to MySQL,
 * then the scheduler publishes the event to a real Kafka topic.
 *
 * Requires Docker. Skipped automatically if Docker is unavailable.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class TransactionOutboxIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8")
            .withDatabaseName("platform")
            .withUsername("root")
            .withPassword("root");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"))
            .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1))
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // Override Kafka bootstrap-servers with the Testcontainers broker
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private TransactionService transactionService;

    /** Called manually — scheduling is disabled via scheduling.enabled=false in test props */
    @Autowired
    private OutboxPublisherScheduler outboxPublisherScheduler;

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void createTransaction_shouldWriteOutboxEvent_andPublishToKafka() {
        UUID customerId = UUID.randomUUID();

        // 1. Create transaction — atomically writes Transaction + OutboxEvent (published=false)
        transactionService.createTransaction(
                customerId, new BigDecimal("500"), "EUR", TransactionType.DEPOSIT);

        assertThat(outboxRepository.findByPublishedFalse())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.getEventType()).isEqualTo("TransactionCreated");
                    assertThat(event.getPayload()).contains("DEPOSIT");
                    assertThat(event.getPayload()).contains("EUR");
                });

        // 2. Manually trigger the OutboxPublisherScheduler (scheduling.enabled=false in tests)
        outboxPublisherScheduler.publishPendingEvents();

        // 3. Consume from Kafka and verify the event arrived on topic=transaction-events
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                kafka.getBootstrapServers(), "tc-test-group", "false");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        consumer.subscribe(List.of("transaction-events"));

        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        consumer.close();

        assertThat(records.count()).isGreaterThan(0);
        String receivedPayload = records.iterator().next().value();
        assertThat(receivedPayload).contains("TransactionCreated");
        assertThat(receivedPayload).contains("DEPOSIT");
        assertThat(receivedPayload).contains("EUR");

        // 4. OutboxEvent is now published=true — no more pending events
        assertThat(outboxRepository.findByPublishedFalse()).isEmpty();
    }
}
