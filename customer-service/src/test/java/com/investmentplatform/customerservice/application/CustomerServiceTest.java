package com.investmentplatform.customerservice.application;

import com.investmentplatform.customerservice.domain.Customer;
import com.investmentplatform.customerservice.domain.CustomerId;
import com.investmentplatform.customerservice.domain.CustomerRepository;
import com.investmentplatform.customerservice.domain.KycStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void registerCustomer_shouldSaveNewCustomerWithPendingKyc() {
        // given
        String name = "Mario Rossi";
        String email = "mario@example.com";
        when(customerRepository.existsByEmail(email)).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        Customer result = customerService.registerCustomer(name, email);

        // then
        assertThat(result.getName()).isEqualTo(name);
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getKycStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(result.getId()).isNotNull();
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void registerCustomer_shouldThrowWhenEmailAlreadyExists() {
        // given
        when(customerRepository.existsByEmail("existing@example.com")).thenReturn(true);

        // then
        assertThatThrownBy(() -> customerService.registerCustomer("Mario", "existing@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        verify(customerRepository, never()).save(any());
    }

    @Test
    void registerCustomer_shouldThrowWhenNameIsBlank() {
        assertThatThrownBy(() -> customerService.registerCustomer("  ", "mario@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Name must not be blank");

        verifyNoInteractions(customerRepository);
    }

    @Test
    void registerCustomer_shouldThrowWhenEmailIsBlank() {
        assertThatThrownBy(() -> customerService.registerCustomer("Mario", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email must not be blank");

        verifyNoInteractions(customerRepository);
    }

    @Test
    void findById_shouldReturnCustomerWhenFound() {
        // given
        Customer customer = Customer.register("Mario Rossi", "mario@example.com");
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        // when
        Optional<Customer> result = customerService.findById(customer.getId());

        // then
        assertThat(result).isPresent().contains(customer);
    }

    @Test
    void findById_shouldReturnEmptyWhenNotFound() {
        // given
        CustomerId id = CustomerId.generate();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        // when
        Optional<Customer> result = customerService.findById(id);

        // then
        assertThat(result).isEmpty();
    }
}
