package com.investmentplatform.transactionservice.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activates Spring's scheduling infrastructure only when {@code scheduling.enabled=true}.
 * Set {@code scheduling.enabled=false} in test properties to prevent the
 * OutboxPublisherScheduler from firing during {@code @SpringBootTest} (no Kafka broker).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
