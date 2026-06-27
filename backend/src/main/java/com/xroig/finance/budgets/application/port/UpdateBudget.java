package com.xroig.finance.budgets.application.port;

import com.xroig.finance.budgets.application.BudgetView;
import com.xroig.finance.budgets.application.port.CreateBudget.BudgetCommand;

/** Inbound port: edit an existing budget. */
public interface UpdateBudget {

    BudgetView update(long id, BudgetCommand command);
}
