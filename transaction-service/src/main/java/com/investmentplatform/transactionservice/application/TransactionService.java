package com.investmentplatform.transactionservice.application;

import com.investmentplatform.transactionservice.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Transaction createTransaction(UUID customerId, BigDecimal amount, String currency, TransactionType type) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID must not be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Transaction type must not be null");
        }

        Money money = Money.of(amount, currency);

        if (type == TransactionType.WITHDRAWAL) {
            validateSufficientBalance(customerId, money.getCurrency(), money.getAmount());
        }

        Transaction transaction = Transaction.create(customerId, money, type);
        return transactionRepository.save(transaction);
    }

    @Transactional(readOnly = true)
    public Optional<Transaction> findById(TransactionId id) {
        return transactionRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Transaction> findByCustomerId(UUID customerId) {
        return transactionRepository.findByCustomerId(customerId);
    }

    /**
     * Computes the simulated balance by summing all non-FAILED transactions
     * for the given customer in the given currency, then checks that the
     * requested withdrawal amount does not exceed it.
     *
     * Rule: WITHDRAWAL cannot bring the balance below zero.
     */
    private void validateSufficientBalance(UUID customerId, String currency, BigDecimal withdrawalAmount) {
        BigDecimal balance = transactionRepository.findByCustomerId(customerId).stream()
                .filter(t -> t.getStatus() != TransactionStatus.FAILED)
                .filter(t -> t.getMoney().getCurrency().equals(currency))
                .map(t -> t.getType() == TransactionType.WITHDRAWAL
                        ? t.getMoney().getAmount().negate()
                        : t.getMoney().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (balance.compareTo(withdrawalAmount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance: " + balance.toPlainString() + " " + currency +
                    " available, " + withdrawalAmount.toPlainString() + " " + currency + " requested"
            );
        }
    }
}
