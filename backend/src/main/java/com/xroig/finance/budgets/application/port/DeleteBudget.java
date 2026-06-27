package com.xroig.finance.budgets.application.port;

/** Inbound port: remove a budget. */
public interface DeleteBudget {

    void delete(long id);
}
