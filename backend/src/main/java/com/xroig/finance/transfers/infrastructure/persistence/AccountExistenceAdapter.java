package com.xroig.finance.transfers.infrastructure.persistence;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaRepository;
import com.xroig.finance.transfers.domain.AccountExistence;
import org.springframework.stereotype.Component;

/**
 * Adapter for {@link AccountExistence}: checks account existence via the accounts store.
 * Explicitly named because other contexts also define an {@code AccountExistenceAdapter}.
 */
@Component("transfersAccountExistenceAdapter")
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
