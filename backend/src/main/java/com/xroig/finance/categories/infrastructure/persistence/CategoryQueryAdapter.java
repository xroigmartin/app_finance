package com.xroig.finance.categories.infrastructure.persistence;

import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaEntity;
import com.xroig.finance.categories.application.CategoryQueryPort;
import com.xroig.finance.categories.application.CategoryView;
import com.xroig.finance.categories.application.CategoryView.AccountRef;
import com.xroig.finance.categories.domain.CategoryId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Read-side adapter (CQRS): assembles {@link CategoryView} read models from the JPA
 * entity graph — nesting the owning account and the parent — without rebuilding the
 * aggregate.
 */
@Component
public class CategoryQueryAdapter implements CategoryQueryPort {

    private final CategoryJpaRepository jpa;

    public CategoryQueryAdapter(CategoryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<CategoryView> findAll() {
        return jpa.findAll().stream().map(CategoryQueryAdapter::toView).toList();
    }

    @Override
    public Optional<CategoryView> findById(CategoryId id) {
        return jpa.findById(id.value()).map(CategoryQueryAdapter::toView);
    }

    /** Assembles a {@link CategoryView} from the entity graph; reused by the categorization read adapter. */
    public static CategoryView toView(CategoryJpaEntity entity) {
        CategoryView parent = entity.getParent() == null ? null : toView(entity.getParent());
        return new CategoryView(entity.getId(), entity.getName(), entity.getType(), entity.getColor(),
                toAccountRef(entity.getAccount()), parent);
    }

    private static AccountRef toAccountRef(AccountJpaEntity account) {
        if (account == null) {
            return null;
        }
        return new AccountRef(account.getId(), account.getName(), account.getType(), account.getInitialBalance());
    }
}
