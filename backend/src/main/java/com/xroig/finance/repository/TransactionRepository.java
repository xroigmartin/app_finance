package com.xroig.finance.repository;

import com.xroig.finance.model.Transaction;
import com.xroig.finance.shared.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByDateBetweenOrderByDateDesc(LocalDate from, LocalDate to);

    @Query("""
            select t from Transaction t
            where t.date >= :from and t.date <= :to
              and (:accountId is null or t.account.id = :accountId)
              and (:categoryId is null or t.category.id = :categoryId)
            order by t.date desc, t.id desc
            """)
    List<Transaction> search(@Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             @Param("accountId") Long accountId,
                             @Param("categoryId") Long categoryId);

    List<Transaction> findTop10ByOrderByDateDescIdDesc();

    boolean existsByAccountId(Long accountId);

    boolean existsByCategoryId(Long categoryId);

    /** True if the category is used by any transaction of a different account. */
    boolean existsByCategoryIdAndAccountIdNot(Long categoryId, Long accountId);

    List<Transaction> findByCategoryId(Long categoryId);

    List<Transaction> findByRefundOfId(Long refundOfId);

    /** Total already refunded for an original movement, optionally excluding one refund (when editing it). */
    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.refundOf.id = :originalId and (:excludeId is null or t.id <> :excludeId)
            """)
    BigDecimal sumRefundedAmount(@Param("originalId") Long originalId,
                                 @Param("excludeId") Long excludeId);

    // A refund flips the sign: it reduces its category's spending and, in the
    // balance, turns its expense back into money in. So every aggregation nets
    // refunds (case when t.refundOf is null then t.amount else -t.amount).

    @Query("""
            select coalesce(sum(case when t.refundOf is null
                                     then (case when t.type = com.xroig.finance.shared.domain.TransactionType.INCOME
                                                then t.amount else -t.amount end)
                                     else (case when t.type = com.xroig.finance.shared.domain.TransactionType.INCOME
                                                then -t.amount else t.amount end) end), 0)
            from Transaction t
            """)
    BigDecimal netTotal();

    @Query("""
            select coalesce(sum(case when t.refundOf is null
                                     then (case when t.type = com.xroig.finance.shared.domain.TransactionType.INCOME
                                                then t.amount else -t.amount end)
                                     else (case when t.type = com.xroig.finance.shared.domain.TransactionType.INCOME
                                                then -t.amount else t.amount end) end), 0)
            from Transaction t where t.account.id = :accountId and t.date <= :until
            """)
    BigDecimal netTotalByAccountUntil(@Param("accountId") Long accountId,
                                      @Param("until") LocalDate until);

    @Query("""
            select coalesce(sum(case when t.refundOf is null then t.amount else -t.amount end), 0)
            from Transaction t
            where t.type = :type and t.date >= :from and t.date <= :to
              and (:accountId is null or t.account.id = :accountId)
            """)
    BigDecimal sumByTypeAndPeriod(@Param("type") TransactionType type,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to,
                                  @Param("accountId") Long accountId);

    @Query("""
            select coalesce(sum(case when t.refundOf is null then t.amount else -t.amount end), 0)
            from Transaction t
            where t.category.id = :categoryId and t.date >= :from and t.date <= :to
              and (:accountId is null or t.account.id = :accountId)
            """)
    BigDecimal sumByCategoryAndPeriod(@Param("categoryId") Long categoryId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to,
                                      @Param("accountId") Long accountId);

    /** Net sum of a category and all its subcategories (one-level roll-up). */
    @Query("""
            select coalesce(sum(case when t.refundOf is null then t.amount else -t.amount end), 0)
            from Transaction t
            where (t.category.id = :categoryId or t.category.parent.id = :categoryId)
              and t.date >= :from and t.date <= :to
              and (:accountId is null or t.account.id = :accountId)
            """)
    BigDecimal sumByCategoryTreeAndPeriod(@Param("categoryId") Long categoryId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to,
                                          @Param("accountId") Long accountId);

    /**
     * Net amount per <em>exact</em> category (no roll-up to the parent) and
     * calendar month for a whole year, scoped to an account when given. Callers
     * that need the per-parent aggregate sum the children themselves. Each row
     * is {@code [categoryId, month (1-12), total]}.
     */
    @Query("""
            select t.category.id, extract(month from t.date),
                   sum(case when t.refundOf is null then t.amount else -t.amount end)
            from Transaction t
            where extract(year from t.date) = :year
              and (:accountId is null or t.account.id = :accountId)
            group by t.category.id, extract(month from t.date)
            """)
    List<Object[]> sumByExactCategoryAndMonthOfYear(@Param("year") int year,
                                                    @Param("accountId") Long accountId);

    /**
     * Net sum by top-level category (subcategories rolled up to their parent).
     * The parent is joined with an explicit {@code left join}: navigating
     * {@code t.category.parent.name} implicitly would force an inner join and
     * silently drop movements on top-level categories (those without a parent).
     */
    @Query("""
            select coalesce(p.name, c.name),
                   coalesce(p.color, c.color),
                   sum(case when t.refundOf is null then t.amount else -t.amount end)
            from Transaction t
                join t.category c
                left join c.parent p
            where t.type = :type and t.date >= :from and t.date <= :to
              and (:accountId is null or t.account.id = :accountId)
            group by coalesce(p.name, c.name), coalesce(p.color, c.color)
            order by sum(case when t.refundOf is null then t.amount else -t.amount end) desc
            """)
    List<Object[]> sumByCategory(@Param("type") TransactionType type,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to,
                                 @Param("accountId") Long accountId);
}
