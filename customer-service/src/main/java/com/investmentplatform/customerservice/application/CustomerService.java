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

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
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
        return customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public Optional<Customer> findById(CustomerId id) {
        return customerRepository.findById(id);
    }
}
