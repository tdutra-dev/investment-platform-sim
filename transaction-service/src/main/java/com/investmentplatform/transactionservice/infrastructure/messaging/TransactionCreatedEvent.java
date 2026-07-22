package com.investmentplatform.transactionservice.infrastructure.messaging;

import java.math.BigDecimal;

/**
 * Immutable DTO representing a TransactionCreated domain event.
 * Serialized as JSON and stored in the outbox_events table payload column.
 * Schema matches the spec (see README § 7).
 */
public record TransactionCreatedEvent(
        String eventType,
        String aggregateId,
        String customerId,
        BigDecimal amount,
        String currency,
        String type,
        String status,
        String occurredAt
) {
}
