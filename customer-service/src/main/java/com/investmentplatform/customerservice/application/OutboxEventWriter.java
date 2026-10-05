package com.investmentplatform.customerservice.application;

import com.investmentplatform.customerservice.domain.Customer;

/**
 * Application port — writes a domain event to the Outbox table in the same DB transaction.
 */
public interface OutboxEventWriter {

    void writeCustomerRegisteredEvent(Customer customer);
}
