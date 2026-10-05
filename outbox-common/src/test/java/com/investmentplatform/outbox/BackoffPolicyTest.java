package com.investmentplatform.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffPolicyTest {

    private final BackoffPolicy policy = new BackoffPolicy(Duration.ofSeconds(1), Duration.ofSeconds(30));

    @Test
    void delayDoublesWithEveryAttempt() {
        assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.delayForAttempt(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(policy.delayForAttempt(4)).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void delayIsCappedAtMax() {
        assertThat(policy.delayForAttempt(6)).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.delayForAttempt(1000)).isEqualTo(Duration.ofSeconds(30));
    }
}
