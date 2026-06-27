package com.xroig.finance.budgets.application.port;

import com.xroig.finance.budgets.application.RecurringBudgetView;

/** Inbound port: read the recurrence of a category. */
public interface FindRecurrence {

    RecurringBudgetView get(long categoryId);
}
