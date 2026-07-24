package com.xroig.finance.investments.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/** Spring Data repository over {@code investments.investment_transaction}. */
public interface InvestmentTransactionJpaRepository
        extends JpaRepository<InvestmentTransactionJpaEntity, Long> {

    List<InvestmentTransactionJpaEntity> findByPortfolioIdOrderByTradeDateAscIdAsc(Long portfolioId);

    /**
     * Filtered, paginated listing (§6): filtering, ordering and paging all resolved
     * in SQL. {@code from}/{@code to} are mandatory (the adapter defaults a null
     * filter to a wide sentinel range) — Postgres cannot infer a bind parameter's
     * data type when it appears only inside an {@code is null} check with no
     * unconditional use, which a {@code LocalDate} param hits but a {@code Long}
     * one (below) does not.
     */
    @Query("""
            select t from InvestmentTransactionJpaEntity t
            where t.portfolioId = :portfolioId
              and (:type is null or t.type = :type)
              and t.tradeDate >= :from
              and t.tradeDate <= :to
              and (:securityId is null or t.securityId = :securityId)
            order by t.tradeDate desc, t.id desc
            """)
    Page<InvestmentTransactionJpaEntity> search(@Param("portfolioId") Long portfolioId,
                                                @Param("type") String type,
                                                @Param("from") LocalDate from,
                                                @Param("to") LocalDate to,
                                                @Param("securityId") Long securityId,
                                                Pageable pageable);

    boolean existsByPortfolioId(Long portfolioId);

    boolean existsBySecurityId(Long securityId);

    boolean existsByPortfolioIdAndExternalId(Long portfolioId, String externalId);
}
