package com.xroig.finance.budgets.infrastructure.persistence;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.budgets.domain.CategoryCatalog;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaEntity;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adapter for {@link CategoryCatalog}: reads a category's id, owning account and whether
 * it has subcategories from the categories store. Explicitly named because the transactions
 * context also defines a {@code CategoryCatalogAdapter}.
 */
@Component("budgetsCategoryCatalogAdapter")
public class CategoryCatalogAdapter implements CategoryCatalog {

    private final CategoryJpaRepository categories;

    public CategoryCatalogAdapter(CategoryJpaRepository categories) {
        this.categories = categories;
    }

    @Override
    public Optional<CategoryBudgetInfo> find(CategoryId id) {
        return categories.findById(id.value()).map(this::toInfo);
    }

    private CategoryBudgetInfo toInfo(CategoryJpaEntity entity) {
        AccountId accountId = entity.getAccount() == null ? null : new AccountId(entity.getAccount().getId());
        boolean hasChildren = categories.existsByParentId(entity.getId());
        return new CategoryBudgetInfo(new CategoryId(entity.getId()), accountId, hasChildren);
    }
}
