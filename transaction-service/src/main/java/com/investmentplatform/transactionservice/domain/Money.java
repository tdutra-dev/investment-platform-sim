package com.investmentplatform.transactionservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object — immutable representation of a monetary amount + currency.
 * Amount is always stored with 4 decimal places. Currency is an ISO 4217 code (e.g. "EUR").
 */
@Embeddable
public final class Money {

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    /** Required by JPA — not for application use. */
    protected Money() {
    }

    public static Money of(BigDecimal amount, String currency) {
        Objects.requireNonNull(amount, "Amount must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");
        if (currency.isBlank()) {
            throw new IllegalArgumentException("Currency must not be blank");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        Money m = new Money();
        m.amount = amount.setScale(4, RoundingMode.HALF_UP);
        m.currency = currency.toUpperCase().strip();
        return m;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return amount.compareTo(money.amount) == 0 && Objects.equals(currency, money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
