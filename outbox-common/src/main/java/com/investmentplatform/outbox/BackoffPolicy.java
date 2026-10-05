package com.investmentplatform.outbox;

import java.time.Duration;

/** Exponential backoff: initial, 2x initial, 4x initial ... capped at max. */
public record BackoffPolicy(Duration initial, Duration max) {

    private static final int MAX_EXPONENT = 30;

    public Duration delayForAttempt(int attempt) {
        int exponent = Math.min(Math.max(attempt - 1, 0), MAX_EXPONENT);
        Duration delay = initial.multipliedBy(1L << exponent);
        return delay.compareTo(max) > 0 ? max : delay;
    }
}
