package com.investmentplatform.customerservice.infrastructure.messaging;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Thin wrapper around {@link KafkaTemplate}. Used only by the outbox scheduler,
 * never from inside a business transaction. The send is synchronous so a row is
 * marked as sent only after the broker acknowledged it (at-least-once delivery).
 */
@Component
public class KafkaEventProducer {

    static final String TOPIC_CUSTOMER_EVENTS = "customer-events";
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String key, String payload) {
        try {
            kafkaTemplate.send(TOPIC_CUSTOMER_EVENTS, key, payload).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing to " + TOPIC_CUSTOMER_EVENTS, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to publish to " + TOPIC_CUSTOMER_EVENTS, e);
        }
    }
}
