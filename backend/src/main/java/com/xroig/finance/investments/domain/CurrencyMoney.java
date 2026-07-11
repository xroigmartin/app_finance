package com.xroig.finance.investments.domain;

import com.xroig.finance.shared.domain.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

/**
 * Monetary amount of the investments context: a value tied to an explicit ISO 4217
 * currency, unlike the shared-kernel {@code Money} (implicit EUR), which stays
 * untouched. Normalized to the context's monetary scale (4 decimals, {@code HALF_UP})
 * on construction so equality is value-based across scales.
 *
 * <p>Arithmetic is only defined within one currency; mixing currencies is a domain
 * error (conversion belongs to the read-side conversion service, RN-7). Immutable;
 * operations return new instances.
 */
public final class CurrencyMoney implements Comparable<CurrencyMoney> {

    private static final int SCALE = 4;

    private final BigDecimal amount;
    private final String currency;

    private CurrencyMoney(BigDecimal amount, String currency) {
        this.amount = Objects.requireNonNull(amount, "amount").setScale(SCALE, RoundingMode.HALF_UP);
        this.currency = requireIsoCurrency(currency);
    }

    public static CurrencyMoney of(BigDecimal amount, String currency) {
        return new CurrencyMoney(amount, currency);
    }

    public static CurrencyMoney of(String amount, String currency) {
        return new CurrencyMoney(new BigDecimal(amount), currency);
    }

    public static CurrencyMoney zero(String currency) {
        return new CurrencyMoney(BigDecimal.ZERO, currency);
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    public CurrencyMoney add(CurrencyMoney other) {
        return new CurrencyMoney(amount.add(requireSameCurrency(other).amount), currency);
    }

    public CurrencyMoney subtract(CurrencyMoney other) {
        return new CurrencyMoney(amount.subtract(requireSameCurrency(other).amount), currency);
    }

    public CurrencyMoney negate() {
        return new CurrencyMoney(amount.negate(), currency);
    }

    public CurrencyMoney abs() {
        return new CurrencyMoney(amount.abs(), currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    @Override
    public int compareTo(CurrencyMoney other) {
        return amount.compareTo(requireSameCurrency(other).amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof CurrencyMoney money
                && amount.equals(money.amount) && currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }

    private static String requireIsoCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new ValidationException("El importe requiere una divisa ISO 4217");
        }
        String normalized = currency.toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(normalized);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Divisa no reconocida (ISO 4217): " + currency);
        }
        return normalized;
    }

    private CurrencyMoney requireSameCurrency(CurrencyMoney other) {
        if (!currency.equals(other.currency)) {
            throw new ValidationException(
                    "Operación entre divisas distintas: " + currency + " y " + other.currency);
        }
        return other;
    }
}
