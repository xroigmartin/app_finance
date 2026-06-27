package com.xroig.finance.budgets.domain;

import com.xroig.finance.shared.domain.ValidationException;

import java.util.ArrayList;
import java.util.List;

/**
 * Value object for the set of active months of a {@link RecurringBudget}, stored as a
 * 12-bit mask ({@code bit 0 = January … bit 11 = December}). Built either from the list of
 * calendar months 1..12 (the language of the API) or from the persisted bitmask. Immutable
 * and equal by value.
 */
public record MonthsMask(int bitmask) {

    /** Builds the mask from calendar months (1..12); rejects any month out of range. */
    public static MonthsMask ofMonths(List<Integer> months) {
        int mask = 0;
        for (Integer m : months) {
            if (m == null || m < 1 || m > 12) {
                throw new ValidationException("Mes no válido: " + m);
            }
            mask |= 1 << (m - 1);
        }
        return new MonthsMask(mask);
    }

    /** Rebuilds the mask from the stored bitmask (trusted, e.g. from the database). */
    public static MonthsMask ofBitmask(int bitmask) {
        return new MonthsMask(bitmask);
    }

    /** True when the given calendar month (1..12) is part of the recurrence. */
    public boolean appliesToMonth(int month) {
        return (bitmask & (1 << (month - 1))) != 0;
    }

    /** The active months as a sorted list of calendar months 1..12. */
    public List<Integer> toMonths() {
        List<Integer> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            if (appliesToMonth(m)) {
                months.add(m);
            }
        }
        return months;
    }
}
