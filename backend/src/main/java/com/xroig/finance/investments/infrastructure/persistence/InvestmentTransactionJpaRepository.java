package com.xroig.finance.investments.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repository over {@code investments.investment_transaction}. */
public interface InvestmentTransactionJpaRepository
        extends JpaRepository<InvestmentTransactionJpaEntity, Long> {

    List<InvestmentTransactionJpaEntity> findByPortfolioIdOrderByTradeDateAscIdAsc(Long portfolioId);

    boolean existsByPortfolioId(Long portfolioId);

    boolean existsBySecurityId(Long securityId);

    boolean existsByPortfolioIdAndExternalId(Long portfolioId, String externalId);
}
