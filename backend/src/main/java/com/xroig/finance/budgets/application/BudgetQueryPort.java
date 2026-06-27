package com.xroig.finance.budgets.application;

import com.xroig.finance.budgets.domain.BudgetId;

import java.util.List;
import java.util.Optional;

/** Read-side (CQRS) outbound port: resolves budget read models from persistence. */
public interface BudgetQueryPort {

    /** Budgets of a month, scoped to an account when given. */
    List<BudgetView> find(int year, int month, Long accountId);

    Optional<BudgetView> findById(BudgetId id);

    /** The annual matrix (12 months × categories) for a year, scoped to an account when given. */
    AnnualBudgetView annual(int year, Long accountId);
}
