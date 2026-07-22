package com.investmentplatform.transactionservice.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around {@link KafkaTemplate} — publishes serialized events to Kafka.
 * Uses async fire-and-forget: the send future is handled via callback (no blocking).
 */
@Component
public class KafkaEventProducer {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventProducer.class);
    private static final String TOPIC_TRANSACTION_EVENTS = "transaction-events";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String key, String payload) {
        kafkaTemplate.send(TOPIC_TRANSACTION_EVENTS, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish to topic={} key={}: {}",
                                TOPIC_TRANSACTION_EVENTS, key, ex.getMessage());
                    } else {
                        log.debug("Published to topic={} partition={} offset={} key={}",
                                TOPIC_TRANSACTION_EVENTS,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset(),
                                key);
                    }
                });
    }
}
