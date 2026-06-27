package com.xroig.finance.categorization.infrastructure.persistence;

import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categorization.domain.CategoryRule;
import com.xroig.finance.categorization.domain.CategoryRuleId;
import com.xroig.finance.categorization.domain.CategoryRuleRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Command-side persistence adapter: implements the {@link CategoryRuleRepository} port over JPA. */
@Component
public class CategoryRulePersistenceAdapter implements CategoryRuleRepository {

    private final CategoryRuleJpaRepository jpa;
    private final CategoryRuleJpaMapper mapper;

    public CategoryRulePersistenceAdapter(CategoryRuleJpaRepository jpa, CategoryRuleJpaMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Optional<CategoryRule> findById(CategoryRuleId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public CategoryRule save(CategoryRule rule) {
        return mapper.toDomain(jpa.save(mapper.toJpa(rule)));
    }

    @Override
    public void deleteById(CategoryRuleId id) {
        jpa.deleteById(id.value());
    }

    @Override
    public boolean existsByCategory(CategoryId categoryId) {
        return jpa.existsByCategoryId(categoryId.value());
    }
}
