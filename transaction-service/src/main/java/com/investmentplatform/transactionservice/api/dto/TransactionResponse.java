package com.investmentplatform.transactionservice.api.dto;

import com.investmentplatform.transactionservice.domain.Transaction;

import java.math.BigDecimal;

public record TransactionResponse(
        String id,
        String customerId,
        BigDecimal amount,
        String currency,
        String type,
        String status,
        String createdAt
) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId().value().toString(),
                t.getCustomerId().toString(),
                t.getMoney().getAmount(),
                t.getMoney().getCurrency(),
                t.getType().name(),
                t.getStatus().name(),
                t.getCreatedAt().toString()
        );
    }
}
