package com.xroig.finance.budgets.domain;

import com.xroig.finance.shared.domain.DomainId;

/** Typed identifier of the {@link RecurringBudget} aggregate. */
public record RecurringBudgetId(Long value) implements DomainId {

    public RecurringBudgetId {
        if (value == null) {
            throw new IllegalArgumentException("RecurringBudgetId requiere un valor no nulo");
        }
    }
}
