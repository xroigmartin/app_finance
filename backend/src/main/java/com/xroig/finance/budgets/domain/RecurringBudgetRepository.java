package com.xroig.finance.budgets.domain;

import com.xroig.finance.categories.domain.CategoryId;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for the {@link RecurringBudget} aggregate. The recurrence is identified by
 * its owning category (1:1), so the command operations key on {@link CategoryId}. Reading
 * the recurrence for the category form goes through {@code RecurringBudgetQueryPort}; this
 * port also feeds the annual matrix with the active recurrences as aggregates so the matrix
 * reuses the planned-amount behavior of the domain.
 */
public interface RecurringBudgetRepository {

    Optional<RecurringBudget> findByCategory(CategoryId categoryId);

    boolean existsByCategory(CategoryId categoryId);

    RecurringBudget save(RecurringBudget recurrence);

    void deleteByCategory(CategoryId categoryId);

    /** Active recurrences feeding the matrix: scoped to an account, or all when {@code accountId} is null. */
    List<RecurringBudget> findActiveByAccount(Long accountId);
}
