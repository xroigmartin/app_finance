package com.xroig.finance.budgets.domain;

import com.xroig.finance.shared.domain.DomainId;

/** Typed identifier of the {@link Budget} aggregate. */
public record BudgetId(Long value) implements DomainId {

    public BudgetId {
        if (value == null) {
            throw new IllegalArgumentException("BudgetId requiere un valor no nulo");
        }
    }
}
