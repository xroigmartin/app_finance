package com.xroig.finance.categories.infrastructure.persistence;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaEntity;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaRepository;
import com.xroig.finance.categories.domain.Category;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categories.domain.CategoryScope;
import org.springframework.stereotype.Component;

/**
 * Translates between the pure {@link Category} aggregate and its {@link CategoryJpaEntity}.
 * On write it resolves the account/parent associations from their ids via
 * {@code getReferenceById} (no extra query); on read it takes only their ids back, so
 * the aggregate stays free of object navigation.
 */
@Component
public class CategoryJpaMapper {

    private final AccountJpaRepository accounts;
    private final CategoryJpaRepository categories;

    public CategoryJpaMapper(AccountJpaRepository accounts, CategoryJpaRepository categories) {
        this.accounts = accounts;
        this.categories = categories;
    }

    public Category toDomain(CategoryJpaEntity entity) {
        CategoryScope scope = entity.getAccount() == null
                ? CategoryScope.global()
                : CategoryScope.boundTo(new AccountId(entity.getAccount().getId()));
        CategoryId parentId = entity.getParent() == null ? null : new CategoryId(entity.getParent().getId());
        return Category.rehydrate(new CategoryId(entity.getId()), entity.getName(), entity.getType(),
                entity.getColor(), scope, parentId);
    }

    public CategoryJpaEntity toJpa(Category category) {
        CategoryJpaEntity entity = new CategoryJpaEntity();
        if (category.id() != null) {
            entity.setId(category.id().value());
        }
        entity.setName(category.name());
        entity.setType(category.type());
        entity.setColor(category.color());
        AccountJpaEntity account = category.scope().accountId()
                .map(id -> accounts.getReferenceById(id.value())).orElse(null);
        entity.setAccount(account);
        entity.setParent(category.parentId() == null
                ? null : categories.getReferenceById(category.parentId().value()));
        return entity;
    }
}
