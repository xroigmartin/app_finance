package com.xroig.finance.investments.domain;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port: how the application persists and reads
 * {@link InvestmentTransaction} aggregates. Besides CRUD, exposes the existence
 * checks behind the RN-5 deletion guards (portfolio/instrument with operations)
 * and the {@code external_id} lookup behind the import idempotency (RN-10).
 */
public interface InvestmentTransactionRepository {

    Optional<InvestmentTransaction> findById(InvestmentTransactionId id);

    /** Operations of one portfolio, ordered by trade date. */
    List<InvestmentTransaction> findByPortfolio(PortfolioId portfolioId);

    InvestmentTransaction save(InvestmentTransaction transaction);

    void deleteById(InvestmentTransactionId id);

    boolean existsByPortfolio(PortfolioId portfolioId);

    boolean existsBySecurity(SecurityId securityId);

    boolean existsByPortfolioAndExternalId(PortfolioId portfolioId, String externalId);
}
