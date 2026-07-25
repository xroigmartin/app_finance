package com.xroig.finance.categorization.application;

import com.xroig.finance.categorization.domain.CategoryRuleId;

import java.util.List;
import java.util.Optional;

/** Read-side port (CQRS): assembles {@link CategoryRuleView} read models for the UI. */
public interface CategoryRuleQueryPort {

    List<CategoryRuleView> findAll();

    Optional<CategoryRuleView> findById(CategoryRuleId id);
}
