package com.xroig.finance.reporting.application;

import java.math.BigDecimal;

/**
 * Read model (CQRS): a monthly budget with what was spent against it (its category tree)
 * and what remains. Each budget is scoped to its account, so the spending is too.
 */
public record BudgetStatusView(Long budgetId,
                               Long accountId,
                               String account,
                               Long categoryId,
                               String category,
                               String color,
                               BigDecimal budget,
                               BigDecimal spent,
                               BigDecimal remaining) {
}
