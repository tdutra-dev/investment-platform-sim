package com.investmentplatform.customerservice.infrastructure.persistence;

import com.investmentplatform.customerservice.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA adapter — package-private, not part of the domain API.
 * Used exclusively by JpaCustomerRepository.
 */
interface SpringDataCustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByEmail(String email);
}
