package com.xroig.finance.budgets.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read model of a category's recurrence, as exposed by the sub-resource
 * ({@code /api/categories/{id}/recurrence}). Months travel as a plain list of integers
 * 1..12 (more readable than the stored bitmask) and {@code validoDesde} as a {@code "YYYY-MM"}
 * string. The amount rows carry their persistence id (the form edits them in place).
 */
public record RecurringBudgetView(Long categoryId,
                                  List<Integer> months,
                                  boolean active,
                                  List<AmountView> amounts) {

    public record AmountView(Long id, BigDecimal amount, String validoDesde) {
    }
}
