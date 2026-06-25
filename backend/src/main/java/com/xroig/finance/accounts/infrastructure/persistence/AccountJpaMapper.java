package com.xroig.finance.accounts.infrastructure.persistence;

import com.xroig.finance.accounts.domain.Account;
import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.shared.domain.Money;
import org.springframework.stereotype.Component;

/** Translates between the pure {@link Account} aggregate and its {@link AccountJpaEntity}. */
@Component
public class AccountJpaMapper {

    public Account toDomain(AccountJpaEntity entity) {
        return Account.rehydrate(
                new AccountId(entity.getId()),
                entity.getName(),
                entity.getType(),
                Money.of(entity.getInitialBalance()));
    }

    public AccountJpaEntity toJpa(Account account) {
        AccountJpaEntity entity = new AccountJpaEntity();
        if (account.id() != null) {
            entity.setId(account.id().value());
        }
        entity.setName(account.name());
        entity.setType(account.type());
        entity.setInitialBalance(account.initialBalance().amount());
        return entity;
    }
}
