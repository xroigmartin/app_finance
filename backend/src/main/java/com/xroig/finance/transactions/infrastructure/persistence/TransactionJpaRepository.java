package com.xroig.finance.transactions.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Spring Data repository over {@link TransactionJpaEntity}; an implementation detail of
 * the adapters. Carries only the queries the transactions use cases and reads need;
 * the aggregation queries used by the (not-yet-migrated) dashboard/budgets remain on
 * the legacy {@code repository.TransactionRepository}.
 */
public interface TransactionJpaRepository extends JpaRepository<TransactionJpaEntity, Long> {

    @Query("""
            select t from TransactionJpaEntity t
            where t.date >= :from and t.date <= :to
              and (:accountId is null or t.account.id = :accountId)
              and (:categoryId is null or t.category.id = :categoryId)
            order by t.date desc, t.id desc
            """)
    List<TransactionJpaEntity> search(@Param("from") LocalDate from,
                                      @Param("to") LocalDate to,
                                      @Param("accountId") Long accountId,
                                      @Param("categoryId") Long categoryId);

    List<TransactionJpaEntity> findTop10ByOrderByDateDescIdDesc();

    /** Total already refunded for an original movement, optionally excluding one refund (when editing it). */
    @Query("""
            select coalesce(sum(t.amount), 0) from TransactionJpaEntity t
            where t.refundOf.id = :originalId and (:excludeId is null or t.id <> :excludeId)
            """)
    BigDecimal sumRefundedAmount(@Param("originalId") Long originalId,
                                 @Param("excludeId") Long excludeId);
}
