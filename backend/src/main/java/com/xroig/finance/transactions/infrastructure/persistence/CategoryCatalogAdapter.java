package com.xroig.finance.transactions.infrastructure.persistence;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaEntity;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaRepository;
import com.xroig.finance.transactions.domain.CategoryCatalog;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Adapter for {@link CategoryCatalog}: reads a category's id and owning account from the categories store. */
@Component
public class CategoryCatalogAdapter implements CategoryCatalog {

    private final CategoryJpaRepository categories;

    public CategoryCatalogAdapter(CategoryJpaRepository categories) {
        this.categories = categories;
    }

    @Override
    public Optional<CategoryDescriptor> find(CategoryId id) {
        return categories.findById(id.value()).map(CategoryCatalogAdapter::toDescriptor);
    }

    private static CategoryDescriptor toDescriptor(CategoryJpaEntity entity) {
        AccountId accountId = entity.getAccount() == null ? null : new AccountId(entity.getAccount().getId());
        return new CategoryDescriptor(new CategoryId(entity.getId()), accountId);
    }
}
