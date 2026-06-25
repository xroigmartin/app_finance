package com.xroig.finance.accounts.domain;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port: how the application persists and reads {@link Account} aggregates.
 * The domain owns the contract; a persistence adapter in {@code infrastructure}
 * implements it (against JPA), so the domain stays free of any storage detail.
 */
public interface AccountRepository {

    List<Account> findAll();

    Optional<Account> findById(AccountId id);

    Account save(Account account);

    void deleteById(AccountId id);
}
