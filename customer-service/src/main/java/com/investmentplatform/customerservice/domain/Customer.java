package com.investmentplatform.customerservice.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root — Customer Management Bounded Context.
 *
 * Identity is owned by the domain: UUID is generated in the factory method
 * before any persistence interaction.
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycStatus kycStatus;

    @Column(nullable = false, updatable = false)
    private LocalDateTime registeredAt;

    /** Required by JPA — not for application use. */
    protected Customer() {
    }

    private Customer(UUID id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.kycStatus = KycStatus.PENDING;
        this.registeredAt = LocalDateTime.now();
    }

    /** Factory method — generates identity and sets initial state. */
    public static Customer register(String name, String email) {
        return new Customer(UUID.randomUUID(), name, email);
    }

    public CustomerId getId() {
        return CustomerId.of(id);
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void verifyKyc() {
        this.kycStatus = KycStatus.VERIFIED;
    }

    public void rejectKyc() {
        this.kycStatus = KycStatus.REJECTED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Customer customer)) return false;
        return Objects.equals(id, customer.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
