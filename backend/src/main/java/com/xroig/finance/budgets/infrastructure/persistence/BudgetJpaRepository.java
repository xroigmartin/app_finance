package com.xroig.finance.budgets.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repository over {@link BudgetJpaEntity}; an implementation detail of the adapters. */
public interface BudgetJpaRepository extends JpaRepository<BudgetJpaEntity, Long> {

    List<BudgetJpaEntity> findByYearAndMonth(Integer year, Integer month);

    List<BudgetJpaEntity> findByAccountIdAndYearAndMonth(Long accountId, Integer year, Integer month);

    List<BudgetJpaEntity> findByYear(Integer year);

    List<BudgetJpaEntity> findByAccountIdAndYear(Long accountId, Integer year);

    boolean existsByAccountIdAndCategoryIdAndYearAndMonth(Long accountId, Long categoryId,
                                                          Integer year, Integer month);

    /** True if any budget targets the category (category deletion guard). */
    boolean existsByCategoryId(Long categoryId);
}
