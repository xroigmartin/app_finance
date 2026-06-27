package com.xroig.finance.budgets.application.port;

/** Inbound port: remove the recurrence of a category (no-op when it has none). */
public interface DeleteRecurrence {

    void delete(long categoryId);
}
