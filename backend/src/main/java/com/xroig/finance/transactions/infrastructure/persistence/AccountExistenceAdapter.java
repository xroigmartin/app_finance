package com.xroig.finance.transactions.infrastructure.persistence;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaRepository;
import com.xroig.finance.transactions.domain.AccountExistence;
import org.springframework.stereotype.Component;

/** Adapter for {@link AccountExistence}: checks account existence via the accounts store. */
@Component("transactionsAccountExistenceAdapter")
public class AccountExistenceAdapter implements AccountExistence {

    private final AccountJpaRepository accounts;

    public AccountExistenceAdapter(AccountJpaRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean exists(AccountId accountId) {
        return accounts.existsById(accountId.value());
    }
}
