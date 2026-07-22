package com.investmentplatform.customerservice.domain;

import java.util.Optional;

/**
 * Domain port — defines persistence operations in pure domain terms.
 * Implemented by JpaCustomerRepository in the infrastructure layer.
 */
public interface CustomerRepository {

    Customer save(Customer customer);

    Optional<Customer> findById(CustomerId id);

    boolean existsByEmail(String email);
}
