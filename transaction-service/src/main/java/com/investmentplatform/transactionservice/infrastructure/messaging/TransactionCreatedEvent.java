package com.investmentplatform.transactionservice.infrastructure.messaging;

import java.math.BigDecimal;

/**
 * Immutable DTO representing a TransactionCreated domain event.
 * Serialized as JSON and stored in the outbox_events table payload column.
 * {@code eventId} uniquely identifies the event so consumers can deduplicate redeliveries.
 */
public record TransactionCreatedEvent(
        String eventId,
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
