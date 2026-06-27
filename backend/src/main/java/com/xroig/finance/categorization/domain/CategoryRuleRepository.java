package com.xroig.finance.categorization.domain;

import com.xroig.finance.categories.domain.CategoryId;

import java.util.Optional;

/**
 * Outbound port for persisting {@link CategoryRule} aggregates on the command side.
 * Listings for the UI go through the read side ({@code CategoryRuleQueryPort}).
 */
public interface CategoryRuleRepository {

    Optional<CategoryRule> findById(CategoryRuleId id);

    CategoryRule save(CategoryRule rule);

    void deleteById(CategoryRuleId id);

    boolean existsByCategory(CategoryId categoryId);
}
