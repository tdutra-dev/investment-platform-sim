package com.investmentplatform.customerservice.infrastructure.persistence;

import com.investmentplatform.customerservice.domain.Customer;
import com.investmentplatform.customerservice.domain.CustomerId;
import com.investmentplatform.customerservice.domain.CustomerRepository;
import com.investmentplatform.customerservice.domain.KycStatus;
import com.investmentplatform.customerservice.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test — verifies JPA repository with a real MySQL 8 container.
 * Each test method runs inside a transaction that is rolled back after the test.
 */
@SpringBootTest
@Transactional
class CustomerRepositoryIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void save_andFindById_shouldPersistAndRetrieveWithCorrectFieldMapping() {
        Customer customer = Customer.register("Mario Rossi", "mario.rossi@example.com");

        customerRepository.save(customer);
        Optional<Customer> found = customerRepository.findById(customer.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Mario Rossi");
        assertThat(found.get().getEmail()).isEqualTo("mario.rossi@example.com");
        assertThat(found.get().getKycStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(found.get().getRegisteredAt()).isNotNull();
    }

    @Test
    void findById_shouldReturnEmpty_whenCustomerDoesNotExist() {
        Optional<Customer> found = customerRepository.findById(CustomerId.generate());

        assertThat(found).isEmpty();
    }

    @Test
    void existsByEmail_shouldReturnTrue_whenCustomerWithEmailExists() {
        customerRepository.save(Customer.register("Exists User", "exists@test.com"));

        assertThat(customerRepository.existsByEmail("exists@test.com")).isTrue();
    }

    @Test
    void existsByEmail_shouldReturnFalse_whenNoCustomerWithEmail() {
        assertThat(customerRepository.existsByEmail("notfound@test.com")).isFalse();
    }
}
