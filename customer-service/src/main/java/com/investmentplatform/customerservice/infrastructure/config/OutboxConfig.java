package com.investmentplatform.customerservice.infrastructure.config;

import com.investmentplatform.outbox.KafkaOutboxPublisher;
import com.investmentplatform.outbox.OutboxProcessor;
import com.investmentplatform.outbox.OutboxProperties;
import com.investmentplatform.outbox.OutboxPublisher;
import com.investmentplatform.customerservice.infrastructure.persistence.OutboxEvent;
import com.investmentplatform.customerservice.infrastructure.persistence.OutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfig {

    @Bean
    OutboxPublisher outboxPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        return new KafkaOutboxPublisher(kafkaTemplate, "customer-events");
    }

    @Bean
    OutboxProcessor<OutboxEvent> outboxProcessor(OutboxRepository repository, OutboxPublisher publisher,
                                                 OutboxProperties properties,
                                                 ObjectProvider<MeterRegistry> registry) {
        return new OutboxProcessor<>("customer", repository, publisher, properties, Clock.systemDefaultZone(),
                registry.getIfAvailable(SimpleMeterRegistry::new)); // @SpringBootTest disables metric exporters
    }
}
