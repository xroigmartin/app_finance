package com.xroig.finance.accounts.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository over {@link AccountJpaEntity}; an implementation detail of the persistence adapter. */
public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, Long> {
}
