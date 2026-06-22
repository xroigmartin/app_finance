package com.xroig.finance.shared.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Money} value object (stage H0a): currency-scale
 * normalization, value-based equality and the arithmetic/sign helpers.
 */
class MoneyTest {

    @Test
    void normalizesToTwoDecimalsHalfUp() {
        assertThat(Money.of("100").amount()).isEqualByComparingTo("100.00");
        assertThat(Money.of("1.005").amount()).isEqualByComparingTo("1.01"); // HALF_UP
    }

    @Test
    void equalityIsValueBasedAcrossScales() {
        assertThat(Money.of("100")).isEqualTo(Money.of("100.00"));
        assertThat(Money.of("100")).hasSameHashCodeAs(Money.of("100.00"));
        assertThat(Money.of("100")).isNotEqualTo(Money.of("100.01"));
    }

    @Test
    void addSubtractAndNegate() {
        assertThat(Money.of("100").add(Money.of("50.50"))).isEqualTo(Money.of("150.50"));
        assertThat(Money.of("100").subtract(Money.of("30"))).isEqualTo(Money.of("70"));
        assertThat(Money.of("40").negate()).isEqualTo(Money.of("-40"));
    }

    @Test
    void signHelpers() {
        assertThat(Money.of("10").isPositive()).isTrue();
        assertThat(Money.of("-10").isNegative()).isTrue();
        assertThat(Money.zero().isZero()).isTrue();
        assertThat(Money.ZERO.isPositive()).isFalse();
    }

    @Test
    void comparable() {
        assertThat(Money.of("10")).isLessThan(Money.of("20"));
        assertThat(Money.of("20")).isGreaterThan(Money.of("10"));
    }

    @Test
    void toStringShowsPlainAmountAndCurrency() {
        assertThat(Money.of("1234.5").toString()).isEqualTo("1234.50 EUR");
    }
}
