package com.xroig.finance.investments.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Quantity} value object (H1.1): a decimal number of
 * titles (fractional shares and FX residues, scale 8) whose comparisons for the
 * sell-without-position rule (RN-4) use the precision tolerance instead of strict
 * equality.
 */
class QuantityTest {

    @Test
    void normalizesToEightDecimalsHalfUp() {
        assertThat(Quantity.of("2.303").value()).isEqualByComparingTo("2.30300000");
        assertThat(Quantity.of("0.000000005").value()).isEqualByComparingTo("0.00000001"); // HALF_UP
    }

    @Test
    void equalityIsValueBasedAcrossScales() {
        assertThat(Quantity.of("10")).isEqualTo(Quantity.of("10.00000000"));
        assertThat(Quantity.of("10")).hasSameHashCodeAs(Quantity.of("10.00000000"));
        assertThat(Quantity.of("10")).isNotEqualTo(Quantity.of("10.00000001"));
    }

    @Test
    void addNegateAndAbs() {
        assertThat(Quantity.of("2.5").add(Quantity.of("1.5"))).isEqualTo(Quantity.of("4"));
        assertThat(Quantity.of("3").negate()).isEqualTo(Quantity.of("-3"));
        assertThat(Quantity.of("-3").abs()).isEqualTo(Quantity.of("3"));
    }

    @Test
    void signHelpers() {
        assertThat(Quantity.of("1").isPositive()).isTrue();
        assertThat(Quantity.of("-1").isNegative()).isTrue();
        assertThat(Quantity.ZERO.isZero()).isTrue();
    }

    @Test
    void comparable() {
        assertThat(Quantity.of("1")).isLessThan(Quantity.of("2"));
        assertThat(Quantity.of("2")).isGreaterThan(Quantity.of("1"));
    }

    @Test
    void exceedsToleratesPrecisionResidues() {
        Quantity position = Quantity.of("10");
        // Residuo de 1e-8 tras compras fraccionadas/splits: no bloquea la venta (RN-4).
        assertThat(Quantity.of("10.00000001").exceeds(position)).isFalse();
        assertThat(Quantity.of("10").exceeds(position)).isFalse();
        // Una diferencia real sí excede.
        assertThat(Quantity.of("10.0000002").exceeds(position)).isTrue();
        assertThat(Quantity.of("11").exceeds(position)).isTrue();
    }

    @Test
    void toStringIsThePlainValue() {
        assertThat(Quantity.of("2.303").toString()).isEqualTo("2.30300000");
    }
}
