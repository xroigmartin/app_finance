package com.xroig.finance.reporting.infrastructure.persistence;

import com.xroig.finance.model.Budget;
import com.xroig.finance.reporting.application.BudgetCatalogQuery;
import com.xroig.finance.repository.BudgetRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves {@link BudgetCatalogQuery}: the budgets of a month, scoped to an account when
 * given (otherwise all), flattened with their account/category fields.
 */
@Component
class BudgetCatalogQueryAdapter implements BudgetCatalogQuery {

    private final BudgetRepository budgets;

    BudgetCatalogQueryAdapter(BudgetRepository budgets) {
        this.budgets = budgets;
    }

    @Override
    public List<ReportBudget> forMonth(int year, int month, Long accountId) {
        List<Budget> rows = accountId != null
                ? budgets.findByAccountIdAndYearAndMonth(accountId, year, month)
                : budgets.findByYearAndMonth(year, month);
        return rows.stream()
                .map(b -> new ReportBudget(b.getId(),
                        b.getAccount().getId(), b.getAccount().getName(),
                        b.getCategory().getId(), b.getCategory().getName(), b.getCategory().getColor(),
                        b.getAmount()))
                .toList();
    }
}
