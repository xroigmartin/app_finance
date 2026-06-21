package com.xroig.finance.repository;

import com.xroig.finance.model.CategoryRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {

    boolean existsByCategoryId(Long categoryId);
}
