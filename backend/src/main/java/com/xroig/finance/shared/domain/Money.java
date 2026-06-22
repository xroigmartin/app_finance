package com.xroig.finance.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Money value object: an amount in euros, the single currency of the app.
 *
 * <p>Normalized to two decimals ({@code HALF_UP}) on construction so the
 * currency invariant holds and equality is value-based ({@code 100} equals
 * {@code 100.00}), with {@link #hashCode()} consistent with {@link #equals(Object)}.
 * Immutable; arithmetic returns new instances.
 */
public final class Money implements Comparable<Money> {

    public static final Money ZERO = Money.of(BigDecimal.ZERO);

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = Objects.requireNonNull(amount, "amount").setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public static Money zero() {
        return ZERO;
    }

    public BigDecimal amount() {
        return amount;
    }

    public Money add(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money negate() {
        return new Money(amount.negate());
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
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Money money && amount.equals(money.amount);
    }

    @Override
    public int hashCode() {
        return amount.hashCode();
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " EUR";
    }
}
