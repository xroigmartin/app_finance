package com.xroig.finance.accounts.domain;

/**
 * Outbound port that answers whether an account is referenced by other aggregates,
 * so the deletion guard can run without the accounts context navigating into the
 * movements/transfers aggregates. An adapter in {@code infrastructure} resolves it
 * (today against the legacy movement/transfer stores).
 */
public interface AccountUsage {

    boolean hasMovements(AccountId id);

    boolean hasTransfers(AccountId id);
}
