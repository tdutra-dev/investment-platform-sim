package com.investmentplatform.customerservice.api.dto;

import com.investmentplatform.customerservice.domain.Customer;

public record CustomerResponse(
        String id,
        String name,
        String email,
        String kycStatus,
        String registeredAt
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId().value().toString(),
                customer.getName(),
                customer.getEmail(),
                customer.getKycStatus().name(),
                customer.getRegisteredAt().toString()
        );
    }
}
