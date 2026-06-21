package com.xroig.finance.repository;

import com.xroig.finance.model.RecurringBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecurringBudgetRepository extends JpaRepository<RecurringBudget, Long> {

    boolean existsByCategoryId(Long categoryId);

    /** Single recurrence of a category, with its amount history eagerly loaded. */
    @Query("select r from RecurringBudget r left join fetch r.amounts where r.category.id = :categoryId")
    Optional<RecurringBudget> findByCategoryIdWithAmounts(@Param("categoryId") Long categoryId);

    /** Active recurrences of the categories owned by an account (for the matrix). */
    @Query("""
            select distinct r from RecurringBudget r
            left join fetch r.amounts
            where r.active = true and r.category.account.id = :accountId
            """)
    List<RecurringBudget> findActiveByAccountWithAmounts(@Param("accountId") Long accountId);

    /** All active recurrences with amounts (for the aggregate matrix, accountId null). */
    @Query("select distinct r from RecurringBudget r left join fetch r.amounts where r.active = true")
    List<RecurringBudget> findAllActiveWithAmounts();
}
