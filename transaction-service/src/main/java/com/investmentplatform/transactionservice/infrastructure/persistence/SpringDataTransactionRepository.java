package com.investmentplatform.transactionservice.infrastructure.persistence;

import com.investmentplatform.transactionservice.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA adapter — package-private, not part of the domain API.
 * Used exclusively by JpaTransactionRepository.
 */
interface SpringDataTransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByCustomerId(UUID customerId);
}
