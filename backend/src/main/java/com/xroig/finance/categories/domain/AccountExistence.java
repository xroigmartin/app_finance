package com.xroig.finance.categories.domain;

import com.xroig.finance.accounts.domain.AccountId;

/**
 * Outbound port to check that an account referenced by a category actually exists.
 * The categories context validates the reference by identity, without depending on
 * the accounts aggregate.
 */
public interface AccountExistence {

    boolean exists(AccountId accountId);
}
