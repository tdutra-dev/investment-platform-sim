package com.investmentplatform.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Columns and state machine of an outbox row: PENDING -> SENT, or PENDING -> (retry with backoff) -> FAILED.
 * Each service extends it with its own table.
 */
@MappedSuperclass
public abstract class OutboxEventBase {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    private UUID id;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, length = 4096)
    private String payload;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 1024)
    private String lastError;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    private LocalDateTime sentAt;

    protected OutboxEventBase() {
    }

    protected void init(UUID id, String aggregateId, String eventType, String payload, LocalDateTime now) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = now;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
    }

    public void markSent(LocalDateTime now) {
        this.status = OutboxStatus.SENT;
        this.sentAt = now;
        this.lastError = null;
    }

    public void markAttemptFailed(String error, LocalDateTime now, BackoffPolicy backoff, int maxAttempts) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH));
        if (this.attempts >= maxAttempts) {
            this.status = OutboxStatus.FAILED;
        } else {
            this.nextAttemptAt = now.plus(backoff.delayForAttempt(this.attempts));
        }
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }
}
