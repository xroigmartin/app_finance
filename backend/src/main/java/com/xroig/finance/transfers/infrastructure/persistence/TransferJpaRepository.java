package com.xroig.finance.transfers.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Spring Data repository over {@link TransferJpaEntity}; an implementation detail of the
 * adapters. Besides the read {@code search}, carries the account deletion guard and the
 * directional in/out totals the reporting context folds into an account's balance.
 */
public interface TransferJpaRepository extends JpaRepository<TransferJpaEntity, Long> {

    @Query("""
            select t from TransferJpaEntity t
            where t.date >= :from and t.date <= :to
              and (:accountId is null or t.fromAccount.id = :accountId or t.toAccount.id = :accountId)
            order by t.date desc, t.id desc
            """)
    List<TransferJpaEntity> search(@Param("from") LocalDate from,
                                   @Param("to") LocalDate to,
                                   @Param("accountId") Long accountId);

    /** True if the account is either side of any transfer (account deletion guard). */
    boolean existsByFromAccountIdOrToAccountId(Long fromAccountId, Long toAccountId);

    @Query("select coalesce(sum(t.amount), 0) from TransferJpaEntity t where t.toAccount.id = :accountId and t.date <= :until")
    BigDecimal totalInUntil(@Param("accountId") Long accountId, @Param("until") LocalDate until);

    @Query("select coalesce(sum(t.amount), 0) from TransferJpaEntity t where t.fromAccount.id = :accountId and t.date <= :until")
    BigDecimal totalOutUntil(@Param("accountId") Long accountId, @Param("until") LocalDate until);
}
