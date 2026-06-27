package com.xroig.finance.budgets.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Spring Data repository over {@link RecurringBudgetJpaEntity}; an implementation detail of the adapters. */
public interface RecurringBudgetJpaRepository extends JpaRepository<RecurringBudgetJpaEntity, Long> {

    boolean existsByCategoryId(Long categoryId);

    /** Single recurrence of a category, with its amount history eagerly loaded. */
    @Query("select r from RecurringBudgetJpaEntity r left join fetch r.amounts where r.category.id = :categoryId")
    Optional<RecurringBudgetJpaEntity> findByCategoryIdWithAmounts(@Param("categoryId") Long categoryId);

    /** Active recurrences of the categories owned by an account (for the matrix). */
    @Query("""
            select distinct r from RecurringBudgetJpaEntity r
            left join fetch r.amounts
            where r.active = true and r.category.account.id = :accountId
            """)
    List<RecurringBudgetJpaEntity> findActiveByAccountWithAmounts(@Param("accountId") Long accountId);

    /** All active recurrences with amounts (for the aggregate matrix, accountId null). */
    @Query("select distinct r from RecurringBudgetJpaEntity r left join fetch r.amounts where r.active = true")
    List<RecurringBudgetJpaEntity> findAllActiveWithAmounts();
}
