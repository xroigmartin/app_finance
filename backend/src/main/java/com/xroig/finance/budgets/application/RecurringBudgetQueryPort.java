package com.xroig.finance.budgets.application;

import com.xroig.finance.categories.domain.CategoryId;

import java.util.Optional;

/**
 * Read-side port (CQRS) for the recurrence sub-resource: assembles the {@link RecurringBudgetView}
 * (with the amount rows' persistence ids) from the store without going through the aggregate.
 */
public interface RecurringBudgetQueryPort {

    Optional<RecurringBudgetView> find(CategoryId categoryId);
}
