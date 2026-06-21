package com.xroig.finance.repository;

import com.xroig.finance.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByYearAndMonth(Integer year, Integer month);

    List<Budget> findByAccountIdAndYearAndMonth(Long accountId, Integer year, Integer month);

    List<Budget> findByYear(Integer year);

    List<Budget> findByAccountIdAndYear(Long accountId, Integer year);

    boolean existsByAccountIdAndCategoryIdAndYearAndMonth(Long accountId, Long categoryId,
                                                          Integer year, Integer month);

    boolean existsByCategoryId(Long categoryId);

    boolean existsByAccountId(Long accountId);
}
