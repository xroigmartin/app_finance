package com.xroig.finance.categorization.infrastructure.persistence;

import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaRepository;
import com.xroig.finance.categorization.domain.CategoryRule;
import com.xroig.finance.categorization.domain.CategoryRuleId;
import org.springframework.stereotype.Component;

/**
 * Translates between the pure {@link CategoryRule} aggregate and its {@link
 * CategoryRuleJpaEntity}. On write it resolves the target category from its id via {@code
 * getReferenceById}; on read it takes only its id back.
 */
@Component
public class CategoryRuleJpaMapper {

    private final CategoryJpaRepository categories;

    public CategoryRuleJpaMapper(CategoryJpaRepository categories) {
        this.categories = categories;
    }

    public CategoryRule toDomain(CategoryRuleJpaEntity entity) {
        return CategoryRule.rehydrate(new CategoryRuleId(entity.getId()), entity.getPattern(),
                new CategoryId(entity.getCategory().getId()));
    }

    public CategoryRuleJpaEntity toJpa(CategoryRule rule) {
        CategoryRuleJpaEntity entity = new CategoryRuleJpaEntity();
        if (rule.id() != null) {
            entity.setId(rule.id().value());
        }
        entity.setPattern(rule.pattern());
        entity.setCategory(categories.getReferenceById(rule.categoryId().value()));
        return entity;
    }
}
