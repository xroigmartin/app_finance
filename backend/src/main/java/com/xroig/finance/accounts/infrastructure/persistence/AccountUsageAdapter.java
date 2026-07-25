package com.xroig.finance.accounts.infrastructure.persistence;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.accounts.domain.AccountUsage;
import com.xroig.finance.transactions.infrastructure.persistence.TransactionJpaRepository;
import com.xroig.finance.transfers.infrastructure.persistence.TransferJpaRepository;
import org.springframework.stereotype.Component;

/**
 * Adapter for the {@link AccountUsage} outbound port. Resolves whether an account is
 * referenced by querying the movements and transfers stores of their migrated contexts.
 */
@Component
public class AccountUsageAdapter implements AccountUsage {

    private final TransactionJpaRepository transactions;
    private final TransferJpaRepository transfers;

    public AccountUsageAdapter(TransactionJpaRepository transactions, TransferJpaRepository transfers) {
        this.transactions = transactions;
        this.transfers = transfers;
    }

    @Override
    public boolean hasMovements(AccountId id) {
        return transactions.existsByAccountId(id.value());
    }

    @Override
    public boolean hasTransfers(AccountId id) {
        return transfers.existsByFromAccountIdOrToAccountId(id.value(), id.value());
    }
}
