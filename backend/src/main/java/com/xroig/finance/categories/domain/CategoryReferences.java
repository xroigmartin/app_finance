package com.xroig.finance.categories.domain;

import com.xroig.finance.accounts.domain.AccountId;

/**
 * Outbound port answering whether a category is referenced by other aggregates,
 * so the deletion and scope-reassignment guards run without the categories context
 * navigating into the movements/budgets/rules/recurrence aggregates.
 */
public interface CategoryReferences {

    boolean hasTransactions(CategoryId id);

    /** Whether the category has movements on an account other than {@code accountId}. */
    boolean hasTransactionsOnOtherAccount(CategoryId id, AccountId accountId);

    boolean hasBudget(CategoryId id);

    boolean hasRule(CategoryId id);

    boolean hasRecurrence(CategoryId id);
}
