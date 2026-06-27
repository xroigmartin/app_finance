package com.xroig.finance.budgets.infrastructure.persistence;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaRepository;
import com.xroig.finance.budgets.domain.AccountExistence;
import org.springframework.stereotype.Component;

/**
 * Adapter for {@link AccountExistence}: checks account existence via the accounts store.
 * Explicitly named because other contexts also define an {@code AccountExistenceAdapter}.
 */
@Component("budgetsAccountExistenceAdapter")
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
