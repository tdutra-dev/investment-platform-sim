package com.investmentplatform.transactionservice.domain;

/**
 * Domain exception thrown when a WITHDRAWAL would push the simulated balance below zero.
 */
public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
