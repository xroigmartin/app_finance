package com.xroig.finance.reporting.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-side (CQRS) outbound port: the budgets of a month (all, or of one account), with
 * the account/category data the budget-status read model needs. Implemented by an adapter
 * in {@code infrastructure}.
 */
public interface BudgetCatalogQuery {

    /** Budgets of {@code year}/{@code month}; scoped to {@code accountId} when given, otherwise all. */
    List<ReportBudget> forMonth(int year, int month, Long accountId);

    /** A budget as the dashboard reads it: its planned amount plus its account/category fields. */
    record ReportBudget(Long budgetId, Long accountId, String accountName,
                        Long categoryId, String categoryName, String categoryColor, BigDecimal amount) {
    }
}
