package com.xroig.finance.budgets.application;

import com.xroig.finance.shared.domain.TransactionType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read model (CQRS) of the annual budget matrix mirroring the user's spreadsheet:
 * for a year (scoped to an account when one is selected), every applicable category
 * with its planned and real amount per calendar month. Rows are split into income
 * and expense; each row carries one cell per month.
 */
public record AnnualBudgetView(int year,
                               Long accountId,
                               List<AnnualRow> income,
                               List<AnnualRow> expense) {

    /**
     * One category row. {@code editable} is true for leaf categories whose cells can
     * take a budget (a top-level category without subcategories, or a subcategory);
     * it is false for a parent category, whose {@code months} are the aggregate
     * (planned and real) of its {@code children} and which is not budgeted directly.
     * {@code children} is empty for leaf rows.
     */
    public record AnnualRow(Long categoryId,
                            String category,
                            String color,
                            TransactionType type,
                            boolean editable,
                            List<MonthCell> months,
                            List<AnnualRow> children) {
    }

    /**
     * One category in one month. {@code budgetId} is null when no budget exists yet
     * for that cell (so the frontend knows whether to create or update) and always
     * null in the aggregate scope (no account selected). {@code actual} is the summed
     * real movement for that category and month.
     */
    public record MonthCell(Long budgetId,
                            BigDecimal budget,
                            BigDecimal actual) {
    }
}
