package com.investmentplatform.outbox;

import java.time.LocalDateTime;
import java.util.UUID;

class TestOutboxEvent extends OutboxEventBase {

    static TestOutboxEvent create(String aggregateId, LocalDateTime now) {
        TestOutboxEvent event = new TestOutboxEvent();
        event.init(UUID.randomUUID(), aggregateId, "TestEvent", "{}", now);
        return event;
    }
}
