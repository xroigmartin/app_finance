package com.xroig.finance.transfers.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring Data repository over {@link TransferJpaEntity}; an implementation detail of the
 * adapters. The aggregation queries used by the (not-yet-migrated) dashboard and the
 * account deletion guard remain on the legacy {@code repository.TransferRepository}.
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
}
