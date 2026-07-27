package com.xroig.finance.investments.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data repository over {@code investments.security}. */
public interface SecurityJpaRepository extends JpaRepository<SecurityJpaEntity, Long> {

    Optional<SecurityJpaEntity> findByIsinAndCurrency(String isin, String currency);
}
