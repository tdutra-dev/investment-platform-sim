package com.investmentplatform.transactionservice.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain port — defines persistence operations in pure domain terms.
 * Implemented by JpaTransactionRepository in the infrastructure layer.
 */
public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findById(TransactionId id);

    List<Transaction> findByCustomerId(UUID customerId);
}
