package com.xroig.finance.budgets.domain;

import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.ValidationException;

import java.time.YearMonth;
import java.util.Objects;

/**
 * One effective-dated amount of a {@link RecurringBudget}: the planned {@link Money} in
 * force from {@link #from} (a calendar month) onwards. Pure value object; the history of
 * these lets a recurring payment change its amount over time while keeping past months on
 * the previous figure.
 */
public record RecurrenceAmount(Money amount, YearMonth from) {

    public RecurrenceAmount {
        Objects.requireNonNull(from, "from");
        if (amount == null || !amount.isPositive()) {
            throw new ValidationException("El importe de la recurrencia debe ser positivo");
        }
    }
}
