package com.xroig.finance.budgets.domain;

import com.xroig.finance.accounts.domain.AccountId;

/** Outbound port to check that the account a budget points at exists. */
public interface AccountExistence {

    boolean exists(AccountId accountId);
}
