package com.xroig.finance.investments.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Number of titles of a position or an operation: a decimal (fractional shares,
 * FX residues) normalized to 8 decimals ({@code HALF_UP}), the precision of the
 * {@code numeric(19,8)} columns.
 *
 * <p>The sell-without-position rule (RN-4) compares quantities with the precision
 * tolerance (one unit of the last decimal) via {@link #exceeds(Quantity)}: closing
 * a position after fractional buys or splits can leave 1e-8 residues that must not
 * block the sale. Immutable; operations return new instances.
 */
public final class Quantity implements Comparable<Quantity> {

    private static final int SCALE = 8;
    private static final BigDecimal TOLERANCE = BigDecimal.ONE.movePointLeft(SCALE);

    public static final Quantity ZERO = Quantity.of(BigDecimal.ZERO);

    private final BigDecimal value;

    private Quantity(BigDecimal value) {
        this.value = Objects.requireNonNull(value, "value").setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Quantity of(BigDecimal value) {
        return new Quantity(value);
    }

    public static Quantity of(String value) {
        return new Quantity(new BigDecimal(value));
    }

    public BigDecimal value() {
        return value;
    }

    public Quantity add(Quantity other) {
        return new Quantity(value.add(other.value));
    }

    public Quantity negate() {
        return new Quantity(value.negate());
    }

    public Quantity abs() {
        return new Quantity(value.abs());
    }

    public boolean isPositive() {
        return value.signum() > 0;
    }

    public boolean isNegative() {
        return value.signum() < 0;
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    /** True when this quantity is greater than {@code other} beyond the precision tolerance (RN-4). */
    public boolean exceeds(Quantity other) {
        return value.subtract(other.value).compareTo(TOLERANCE) > 0;
    }

    @Override
    public int compareTo(Quantity other) {
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Quantity quantity && value.equals(quantity.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
