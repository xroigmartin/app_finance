package com.xroig.finance.budgets.application.port;

import com.xroig.finance.budgets.application.BudgetView;

import java.util.List;

/** Inbound port: copy the budgets of one month into another. */
public interface CopyBudgets {

    List<BudgetView> copy(CopyCommand command);

    /** Intent to copy budgets between two months. */
    record CopyCommand(int fromYear, int fromMonth, int toYear, int toMonth) {
    }
}
