package com.xroig.finance.reporting.infrastructure.persistence;

import com.xroig.finance.transactions.infrastructure.persistence.TransactionJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Not bound to a single aggregate (the generic type is a placeholder, never used by
 * a derived query): hosts the {@code union all} that orders transactions and
 * transfers together, so Spring Data doesn't need a real entity/id pair. The keys
 * query returns a {@code List} (no count) since a page's rows don't need a total;
 * {@link #countTransactions}/{@link #countTransfers} resolve {@code totalElements}
 * with two plain, non-{@code union} counts instead of counting the combined query
 * (HQL doesn't support a {@code union} as a derived table, only as a top-level query).
 */
interface MovementKeyRepository extends Repository<TransactionJpaEntity, Long> {

    /**
     * Native, not HQL: Hibernate 7.1's HQL-to-SQL translation of a two-branch
     * {@code union all} scopes a trailing {@code order by} to only the last
     * branch (wrong parenthesization), so the combined result comes back
     * unordered across sources. Native SQL is passed through verbatim, so the
     * {@code order by} unambiguously applies to the whole union.
     */
    @Query(value = """
            (select t.id as id, t.date as sortdate, 'tx' as source
             from transactions t
             where t.date >= :from and t.date <= :to
               and (:accountId is null or t.account_id = :accountId)
               and (:categoryId is null or t.category_id = :categoryId))
            union all
            (select tr.id as id, tr.date as sortdate, 'tr' as source
             from transfers tr
             where tr.date >= :from and tr.date <= :to
               and (:accountId is null or tr.from_account_id = :accountId or tr.to_account_id = :accountId)
               and (:categoryId is null))
            order by sortdate desc, id desc
            """, nativeQuery = true)
    List<MovementKeyProjection> searchKeys(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                           @Param("accountId") Long accountId, @Param("categoryId") Long categoryId,
                                           Pageable pageable);

    @Query("""
            select count(t) from TransactionJpaEntity t
            where t.date >= :from and t.date <= :to
              and (:accountId is null or t.account.id = :accountId)
              and (:categoryId is null or t.category.id = :categoryId)
            """)
    long countTransactions(@Param("from") LocalDate from, @Param("to") LocalDate to,
                           @Param("accountId") Long accountId, @Param("categoryId") Long categoryId);

    @Query("""
            select count(tr) from TransferJpaEntity tr
            where tr.date >= :from and tr.date <= :to
              and (:accountId is null or tr.fromAccount.id = :accountId or tr.toAccount.id = :accountId)
              and (:categoryId is null)
            """)
    long countTransfers(@Param("from") LocalDate from, @Param("to") LocalDate to,
                        @Param("accountId") Long accountId, @Param("categoryId") Long categoryId);

    interface MovementKeyProjection {
        Long getId();
        LocalDate getSortDate();
        String getSource();
    }
}
