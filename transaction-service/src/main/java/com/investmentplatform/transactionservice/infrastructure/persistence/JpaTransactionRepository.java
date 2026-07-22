package com.investmentplatform.transactionservice.infrastructure.persistence;

import com.investmentplatform.transactionservice.domain.Transaction;
import com.investmentplatform.transactionservice.domain.TransactionId;
import com.investmentplatform.transactionservice.domain.TransactionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter — implements the domain TransactionRepository port using Spring Data JPA.
 */
@Repository
public class JpaTransactionRepository implements TransactionRepository {

    private final SpringDataTransactionRepository springDataRepo;

    public JpaTransactionRepository(SpringDataTransactionRepository springDataRepo) {
        this.springDataRepo = springDataRepo;
    }

    @Override
    public Transaction save(Transaction transaction) {
        return springDataRepo.save(transaction);
    }

    @Override
    public Optional<Transaction> findById(TransactionId id) {
        return springDataRepo.findById(id.value());
    }

    @Override
    public List<Transaction> findByCustomerId(UUID customerId) {
        return springDataRepo.findByCustomerId(customerId);
    }
}
