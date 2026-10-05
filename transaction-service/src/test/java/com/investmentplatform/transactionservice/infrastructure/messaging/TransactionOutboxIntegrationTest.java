package com.investmentplatform.transactionservice.infrastructure.messaging;

import com.investmentplatform.transactionservice.application.TransactionService;
import com.investmentplatform.transactionservice.domain.TransactionType;
import com.investmentplatform.outbox.OutboxProcessor;
import com.investmentplatform.outbox.OutboxProperties;
import com.investmentplatform.outbox.OutboxPublisher;
import com.investmentplatform.outbox.OutboxStatus;
import com.investmentplatform.transactionservice.infrastructure.persistence.OutboxEvent;
import com.investmentplatform.transactionservice.infrastructure.persistence.OutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test of the Outbox against real MySQL and Kafka:
 * 1) Transaction + OutboxEvent are written atomically and the scheduler publishes to Kafka;
 * 2) two processors running concurrently on the same table publish every event exactly once.
 *
 * Requires Docker. Skipped automatically if Docker is unavailable.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class TransactionOutboxIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.3")
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

    /** Called manually: scheduling is disabled via scheduling.enabled=false in the test properties. */
    @Autowired
    private OutboxPublisherScheduler outboxPublisherScheduler;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanOutbox() {
        outboxRepository.deleteAll();
    }

    @Test
    void createTransaction_shouldWriteOutboxEvent_andPublishToKafka() {
        String transactionId = transactionService.createTransaction(
                UUID.randomUUID(), new BigDecimal("500"), "EUR", TransactionType.DEPOSIT).getId().value().toString();

        assertThat(outboxRepository.findAll())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
                    assertThat(event.getEventType()).isEqualTo("TransactionCreated");
                    assertThat(event.getPayload()).contains("DEPOSIT").contains("EUR");
                });

        outboxPublisherScheduler.publishPendingEvents();

        List<String> payloads = consumeAll(1);
        assertThat(payloads).anySatisfy(p ->
                assertThat(p).contains(transactionId).contains("TransactionCreated").contains("DEPOSIT"));
        assertThat(outboxRepository.countByStatus(OutboxStatus.PENDING)).isZero();
        assertThat(outboxRepository.findAll()).allSatisfy(e -> {
            assertThat(e.getStatus()).isEqualTo(OutboxStatus.SENT);
            assertThat(e.getSentAt()).isNotNull();
        });
    }

    @Test
    void twoConcurrentProcessors_shouldPublishEveryEventExactlyOnce() throws Exception {
        int total = 200;
        Set<String> expectedIds = new HashSet<>();
        for (int i = 0; i < total; i++) {
            UUID id = UUID.randomUUID();
            expectedIds.add(id.toString());
            outboxRepository.save(OutboxEvent.create(id, "agg-" + i, "TransactionCreated",
                    "{\"eventId\":\"" + id + "\"}"));
        }

        // Small batches so the two instances keep interleaving on the same table
        OutboxProperties properties = new OutboxProperties(5, 5,
                new OutboxProperties.Backoff(Duration.ofSeconds(1), Duration.ofMinutes(1)), Duration.ofDays(7));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Integer> instance = () -> {
            OutboxProcessor<OutboxEvent> processor = new OutboxProcessor<>("transaction", outboxRepository,
                    outboxPublisher, properties, Clock.systemDefaultZone(), new SimpleMeterRegistry());
            start.await();
            int sent = 0;
            int batch;
            do {
                batch = tx.execute(status -> processor.publishBatch());
                sent += batch;
            } while (batch > 0);
            return sent;
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = pool.submit(instance);
            Future<Integer> b = pool.submit(instance);
            start.countDown();
            assertThat(a.get() + b.get()).isEqualTo(total);
        } finally {
            pool.shutdownNow();
        }

        assertThat(outboxRepository.countByStatus(OutboxStatus.SENT)).isEqualTo(total);

        // The topic is shared with the other test, so only look at the events created here
        List<String> publishedIds = consumeAll(total).stream()
                .map(payload -> payload.replaceAll(".*\"eventId\":\"([^\"]+)\".*", "$1"))
                .filter(expectedIds::contains)
                .toList();
        assertThat(publishedIds).hasSize(total).doesNotHaveDuplicates();
        assertThat(new HashSet<>(publishedIds)).isEqualTo(expectedIds);
    }

    /** Reads transaction-events from the beginning until {@code expected} records arrived, then waits briefly for extras. */
    private List<String> consumeAll(int expected) {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                kafka.getBootstrapServers(), "tc-test-" + UUID.randomUUID(), "false");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        List<String> payloads = new ArrayList<>();
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of("transaction-events"));
            long deadline = System.currentTimeMillis() + 30_000;
            while (payloads.size() < expected && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> payloads.add(r.value()));
            }
            consumer.poll(Duration.ofSeconds(2)).forEach(r -> payloads.add(r.value()));
        }
        return payloads;
    }
}
