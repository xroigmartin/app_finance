package com.xroig.finance.transactions.domain;

import com.xroig.finance.accounts.domain.AccountId;

/** Outbound port to check that the account a movement points at exists. */
public interface AccountExistence {

    boolean exists(AccountId accountId);
}
