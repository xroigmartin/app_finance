package com.xroig.finance.categorization.infrastructure.persistence;

import com.xroig.finance.categories.infrastructure.persistence.CategoryQueryAdapter;
import com.xroig.finance.categorization.application.CategoryRuleQueryPort;
import com.xroig.finance.categorization.application.CategoryRuleView;
import com.xroig.finance.categorization.domain.CategoryRuleId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Read-side adapter (CQRS): assembles {@link CategoryRuleView} read models from the JPA
 * entity graph, nesting the target category by reusing the categories context's view
 * assembler ({@link CategoryQueryAdapter#toView}) — without rebuilding the aggregate.
 */
@Component
public class CategoryRuleQueryAdapter implements CategoryRuleQueryPort {

    private final CategoryRuleJpaRepository jpa;

    public CategoryRuleQueryAdapter(CategoryRuleJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<CategoryRuleView> findAll() {
        return jpa.findAll().stream().map(CategoryRuleQueryAdapter::toView).toList();
    }

    @Override
    public Optional<CategoryRuleView> findById(CategoryRuleId id) {
        return jpa.findById(id.value()).map(CategoryRuleQueryAdapter::toView);
    }

    private static CategoryRuleView toView(CategoryRuleJpaEntity entity) {
        return new CategoryRuleView(entity.getId(), entity.getPattern(),
                CategoryQueryAdapter.toView(entity.getCategory()));
    }
}
