package com.xroig.finance.budgets.application.port;

import com.xroig.finance.budgets.application.BudgetView;

import java.math.BigDecimal;

/** Inbound port: budget a category on an account for a month. */
public interface CreateBudget {

    BudgetView create(BudgetCommand command);

    /** Intent to create/update a budget. */
    record BudgetCommand(Long accountId, Long categoryId, int year, int month, BigDecimal amount) {
    }
}
