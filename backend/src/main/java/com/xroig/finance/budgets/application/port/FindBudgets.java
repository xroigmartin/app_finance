package com.xroig.finance.budgets.application.port;

import com.xroig.finance.budgets.application.AnnualBudgetView;
import com.xroig.finance.budgets.application.BudgetView;

import java.util.List;

/** Inbound port: read budgets, either as a per-month list or as the annual matrix. */
public interface FindBudgets {

    List<BudgetView> find(int year, int month, Long accountId);

    AnnualBudgetView annual(int year, Long accountId);
}
