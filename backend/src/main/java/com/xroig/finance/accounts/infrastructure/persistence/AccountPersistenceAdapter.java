package com.xroig.finance.accounts.infrastructure.persistence;

import com.xroig.finance.accounts.domain.Account;
import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.accounts.domain.AccountRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Persistence adapter: implements the {@link AccountRepository} outbound port over JPA. */
@Component
public class AccountPersistenceAdapter implements AccountRepository {

    private final AccountJpaRepository jpa;
    private final AccountJpaMapper mapper;

    public AccountPersistenceAdapter(AccountJpaRepository jpa, AccountJpaMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public List<Account> findAll() {
        return jpa.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Account save(Account account) {
        return mapper.toDomain(jpa.save(mapper.toJpa(account)));
    }

    @Override
    public void deleteById(AccountId id) {
        jpa.deleteById(id.value());
    }
}
