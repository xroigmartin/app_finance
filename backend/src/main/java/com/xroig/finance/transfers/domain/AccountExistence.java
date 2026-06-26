package com.xroig.finance.transfers.domain;

import com.xroig.finance.accounts.domain.AccountId;

/** Outbound port to check that an account a transfer points at exists. */
public interface AccountExistence {

    boolean exists(AccountId accountId);
}
