package com.investmentplatform.customerservice.api;

import com.investmentplatform.customerservice.api.dto.CreateCustomerRequest;
import com.investmentplatform.customerservice.api.dto.CustomerResponse;
import com.investmentplatform.customerservice.application.CustomerService;
import com.investmentplatform.customerservice.domain.CustomerId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> registerCustomer(@RequestBody CreateCustomerRequest request) {
        var customer = customerService.registerCustomer(request.name(), request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(CustomerResponse.from(customer));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomer(@PathVariable String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        return customerService.findById(CustomerId.of(uuid))
                .map(CustomerResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
