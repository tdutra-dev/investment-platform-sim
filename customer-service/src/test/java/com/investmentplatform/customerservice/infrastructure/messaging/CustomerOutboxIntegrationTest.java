package com.investmentplatform.customerservice.infrastructure.messaging;

import com.investmentplatform.customerservice.application.CustomerService;
import com.investmentplatform.customerservice.infrastructure.persistence.OutboxEvent;
import com.investmentplatform.customerservice.infrastructure.persistence.OutboxRepository;
import com.investmentplatform.outbox.OutboxProcessor;
import com.investmentplatform.outbox.OutboxProperties;
import com.investmentplatform.outbox.OutboxStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import com.investmentplatform.customerservice.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Customer outbox on a real MySQL 8: registering a customer writes a PENDING row in the same transaction,
 * the processor claims it with FOR UPDATE SKIP LOCKED and marks it SENT, and cleanup removes old SENT rows.
 * Kafka is replaced by a recording publisher (the Kafka path is covered in transaction-service).
 */
@SpringBootTest
class CustomerOutboxIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanOutbox() {
        outboxRepository.deleteAll();
    }

    @Test
    void registerCustomer_shouldWriteOutboxRow_thenPublishAndCleanUp() throws InterruptedException {
        var customer = customerService.registerCustomer("Grace Hopper", "grace@example.com");

        assertThat(outboxRepository.findAll()).singleElement().satisfies(e -> {
            assertThat(e.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(e.getEventType()).isEqualTo("CustomerRegistered");
            assertThat(e.getAggregateId()).isEqualTo(customer.getId().value().toString());
        });

        List<String> published = new ArrayList<>();
        OutboxProperties properties = new OutboxProperties(10, 3,
                new OutboxProperties.Backoff(Duration.ofSeconds(1), Duration.ofMinutes(1)), Duration.ZERO);
        OutboxProcessor<OutboxEvent> processor = new OutboxProcessor<>("customer", outboxRepository,
                (key, payload) -> published.add(key), properties, Clock.systemDefaultZone(), new SimpleMeterRegistry());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThat(tx.<Integer>execute(s -> processor.publishBatch())).isEqualTo(1);
        assertThat(published).containsExactly(customer.getId().value().toString());
        assertThat(outboxRepository.countByStatus(OutboxStatus.SENT)).isEqualTo(1);
        assertThat(tx.<Integer>execute(s -> processor.publishBatch())).isZero();

        Thread.sleep(20);
        assertThat(tx.<Integer>execute(s -> processor.cleanup())).isEqualTo(1);
        assertThat(outboxRepository.count()).isZero();
    }
}
