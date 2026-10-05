package com.investmentplatform.customerservice.application;

import com.investmentplatform.customerservice.domain.Customer;
import com.investmentplatform.customerservice.domain.CustomerId;
import com.investmentplatform.customerservice.domain.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;

    private final OutboxEventWriter outboxEventWriter;

    public CustomerService(CustomerRepository customerRepository, OutboxEventWriter outboxEventWriter) {
        this.customerRepository = customerRepository;
        this.outboxEventWriter = outboxEventWriter;
    }

    public Customer registerCustomer(String name, String email) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        if (customerRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("A customer with email '" + email + "' already exists");
        }
        Customer customer = Customer.register(name, email);
        Customer saved = customerRepository.save(customer);
        // Outbox: same transaction as the customer insert, published to Kafka later by the scheduler
        outboxEventWriter.writeCustomerRegisteredEvent(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Customer> findById(CustomerId id) {
        return customerRepository.findById(id);
    }
}
