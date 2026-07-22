package com.investmentplatform.auditlogservice.api.dto;

import com.investmentplatform.auditlogservice.infrastructure.persistence.AuditLogEntry;

public record AuditLogEntryResponse(
        String id,
        String aggregateId,
        String eventType,
        String topic,
        String payload,
        String receivedAt
) {
    public static AuditLogEntryResponse from(AuditLogEntry entry) {
        return new AuditLogEntryResponse(
                entry.getId(),
                entry.getAggregateId(),
                entry.getEventType(),
                entry.getTopic(),
                entry.getPayload(),
                entry.getReceivedAt().toString()
        );
    }
}
