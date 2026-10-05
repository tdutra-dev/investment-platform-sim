package com.investmentplatform.outbox;

/** Sends one event to the broker and returns only after the broker acknowledged it; throws otherwise. */
public interface OutboxPublisher {

    void publish(String key, String payload);
}
