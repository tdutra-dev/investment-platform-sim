package com.investmentplatform.customerservice.infrastructure.messaging;

/**
 * Immutable DTO for the CustomerRegistered event, stored as JSON in the outbox payload.
 * {@code eventId} lets consumers deduplicate redeliveries.
 */
public record CustomerRegisteredEvent(
        String eventId,
        String eventType,
        String aggregateId,
        String name,
        String email,
        String kycStatus,
        String occurredAt
) {
}
