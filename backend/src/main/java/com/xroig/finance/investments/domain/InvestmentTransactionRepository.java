package com.xroig.finance.investments.domain;

import com.xroig.finance.shared.domain.Page;

import java.time.LocalDate;
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

    /** Operations of one portfolio, ordered by trade date. Unbounded: for domain calculations, never a listing. */
    List<InvestmentTransaction> findByPortfolio(PortfolioId portfolioId);

    /**
     * Paginated, filtered listing of a portfolio's operations (§6), newest first;
     * a null filter field does not filter. Filtering, ordering and paging all
     * happen at the database, unlike {@link #findByPortfolio(PortfolioId)}.
     */
    Page<InvestmentTransaction> search(PortfolioId portfolioId, InvestmentTransactionType type,
                                       LocalDate from, LocalDate to, SecurityId securityId,
                                       int page, int size);

    InvestmentTransaction save(InvestmentTransaction transaction);

    void deleteById(InvestmentTransactionId id);

    boolean existsByPortfolio(PortfolioId portfolioId);

    boolean existsBySecurity(SecurityId securityId);

    boolean existsByPortfolioAndExternalId(PortfolioId portfolioId, String externalId);
}
