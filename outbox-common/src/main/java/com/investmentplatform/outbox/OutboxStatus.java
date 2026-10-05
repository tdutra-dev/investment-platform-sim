package com.investmentplatform.outbox;

public enum OutboxStatus {
    PENDING,
    SENT,
    /** Max attempts reached; needs manual attention. */
    FAILED
}
