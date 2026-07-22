package com.investmentplatform.transactionservice.api.dto;

import java.math.BigDecimal;

public record CreateTransactionRequest(
        String customerId,
        BigDecimal amount,
        String currency,
        String type
) {
}
