package com.investmentplatform.transactionservice.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root — Transaction Processing Bounded Context.
 *
 * {@code customerId} is a reference by ID only (DDD cross-aggregate rule:
 * aggregates from different bounded contexts are never referenced directly by object).
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID customerId;

    @Embedded
    private Money money;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Required by JPA — not for application use. */
    protected Transaction() {
    }

    private Transaction(UUID id, UUID customerId, Money money, TransactionType type) {
        this.id = id;
        this.customerId = customerId;
        this.money = money;
        this.type = type;
        this.status = TransactionStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    /** Factory method — creates a new transaction in PENDING state. */
    public static Transaction create(UUID customerId, Money money, TransactionType type) {
        return new Transaction(UUID.randomUUID(), customerId, money, type);
    }

    public TransactionId getId() {
        return TransactionId.of(id);
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public Money getMoney() {
        return money;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void complete() {
        this.status = TransactionStatus.COMPLETED;
    }

    public void fail() {
        this.status = TransactionStatus.FAILED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction tx)) return false;
        return Objects.equals(id, tx.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
