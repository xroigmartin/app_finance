package com.xroig.finance.budgets.application.port;

import com.xroig.finance.budgets.application.RecurringBudgetView;

import java.math.BigDecimal;
import java.util.List;

/** Inbound port: create or replace the recurrence of a leaf, account-bound category. */
public interface UpsertRecurrence {

    RecurringBudgetView upsert(long categoryId, RecurrenceCommand command);

    /** Intent to set a recurrence: active months (1..12) and the effective-dated amount history. */
    record RecurrenceCommand(List<Integer> months, boolean active, List<AmountInput> amounts) {
    }

    /** One amount with its effective month as a {@code "YYYY-MM"} string (parsed in the service). */
    record AmountInput(BigDecimal amount, String validoDesde) {
    }
}
