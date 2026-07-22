package com.investmentplatform.customerservice.infrastructure.persistence;

import com.investmentplatform.customerservice.domain.Customer;
import com.investmentplatform.customerservice.domain.CustomerId;
import com.investmentplatform.customerservice.domain.CustomerRepository;
import com.investmentplatform.customerservice.domain.KycStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test — verifies JPA repository with a real MySQL 8 container.
 * Each test method runs inside a transaction that is rolled back after the test.
 * Requires Docker. Skipped automatically if Docker is unavailable.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class CustomerRepositoryIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8")
            .withDatabaseName("platform")
            .withUsername("root")
            .withPassword("root");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

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
