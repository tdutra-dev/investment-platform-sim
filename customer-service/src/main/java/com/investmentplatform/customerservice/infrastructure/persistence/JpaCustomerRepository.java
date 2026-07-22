package com.investmentplatform.customerservice.infrastructure.persistence;

import com.investmentplatform.customerservice.domain.Customer;
import com.investmentplatform.customerservice.domain.CustomerId;
import com.investmentplatform.customerservice.domain.CustomerRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Adapter — implements the domain CustomerRepository port using Spring Data JPA.
 */
@Repository
public class JpaCustomerRepository implements CustomerRepository {

    private final SpringDataCustomerRepository springDataRepo;

    public JpaCustomerRepository(SpringDataCustomerRepository springDataRepo) {
        this.springDataRepo = springDataRepo;
    }

    @Override
    public Customer save(Customer customer) {
        return springDataRepo.save(customer);
    }

    @Override
    public Optional<Customer> findById(CustomerId id) {
        return springDataRepo.findById(id.value());
    }

    @Override
    public boolean existsByEmail(String email) {
        return springDataRepo.existsByEmail(email);
    }
}
