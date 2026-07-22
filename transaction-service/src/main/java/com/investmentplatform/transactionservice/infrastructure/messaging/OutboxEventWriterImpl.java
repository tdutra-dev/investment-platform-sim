package com.investmentplatform.transactionservice.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmentplatform.transactionservice.application.OutboxEventWriter;
import com.investmentplatform.transactionservice.domain.Transaction;
import com.investmentplatform.transactionservice.infrastructure.persistence.OutboxEvent;
import com.investmentplatform.transactionservice.infrastructure.persistence.OutboxRepository;
import org.springframework.stereotype.Component;

/**
 * Implements {@link OutboxEventWriter} by serializing the domain event to JSON
 * and persisting it to the outbox_events table.
 *
 * Called within the same {@code @Transactional} boundary as
 * {@link com.investmentplatform.transactionservice.application.TransactionService#createTransaction},
 * guaranteeing atomic write of Transaction + OutboxEvent.
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
    public void writeTransactionCreatedEvent(Transaction transaction) {
        TransactionCreatedEvent event = new TransactionCreatedEvent(
                "TransactionCreated",
                transaction.getId().value().toString(),
                transaction.getCustomerId().toString(),
                transaction.getMoney().getAmount(),
                transaction.getMoney().getCurrency(),
                transaction.getType().name(),
                transaction.getStatus().name(),
                transaction.getCreatedAt().toString()
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize TransactionCreatedEvent for aggregate "
                    + event.aggregateId(), e);
        }

        outboxRepository.save(OutboxEvent.create(event.aggregateId(), event.eventType(), payload));
    }
}
