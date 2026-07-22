package com.investmentplatform.transactionservice.application;

import com.investmentplatform.transactionservice.domain.Transaction;

/**
 * Application port — writes a domain event to the Outbox table in the same DB transaction.
 * Implemented by {@link com.investmentplatform.transactionservice.infrastructure.messaging.OutboxEventWriterImpl}.
 */
public interface OutboxEventWriter {

    void writeTransactionCreatedEvent(Transaction transaction);
}
