package com.xroig.finance.budgets.domain;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.domain.CategoryId;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting {@link Budget} aggregates on the command side.
 * Listings for the UI (the per-month list and the annual matrix) go through the
 * read side ({@code BudgetQueryPort}).
 */
public interface BudgetRepository {

    Optional<Budget> findById(BudgetId id);

    Budget save(Budget budget);

    void deleteById(BudgetId id);

    /** Whether a budget already occupies the given account/category/year/month slot. */
    boolean existsAt(AccountId accountId, CategoryId categoryId, int year, int month);

    /** Budgets of a whole month across categories (source of the copy use case). */
    List<Budget> findByYearMonth(int year, int month);
}
