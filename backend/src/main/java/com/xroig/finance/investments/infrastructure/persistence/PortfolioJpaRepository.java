package com.xroig.finance.investments.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository over {@code investments.portfolio}. */
public interface PortfolioJpaRepository extends JpaRepository<PortfolioJpaEntity, Long> {
}
