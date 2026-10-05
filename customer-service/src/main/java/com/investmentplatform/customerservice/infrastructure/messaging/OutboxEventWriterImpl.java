package com.investmentplatform.customerservice.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmentplatform.customerservice.application.OutboxEventWriter;
import com.investmentplatform.customerservice.domain.Customer;
import com.investmentplatform.customerservice.infrastructure.persistence.OutboxEvent;
import com.investmentplatform.customerservice.infrastructure.persistence.OutboxRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Serializes the domain event and saves it to the outbox table. Runs inside the
 * caller's transaction, so the customer row and the outbox row commit atomically.
 */
@Component
public class OutboxEventWriterImpl implements OutboxEventWriter {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventWriterImpl(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void writeCustomerRegisteredEvent(Customer customer) {
        UUID eventId = UUID.randomUUID();
        CustomerRegisteredEvent event = new CustomerRegisteredEvent(
                eventId.toString(),
                "CustomerRegistered",
                customer.getId().value().toString(),
                customer.getName(),
                customer.getEmail(),
                customer.getKycStatus().name(),
                customer.getRegisteredAt().toString()
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize CustomerRegisteredEvent for " + event.aggregateId(), e);
        }

        outboxRepository.save(OutboxEvent.create(eventId, event.aggregateId(), event.eventType(), payload));
    }
}
