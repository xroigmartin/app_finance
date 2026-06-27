package com.xroig.finance.categorization.infrastructure.persistence;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaEntity;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaRepository;
import com.xroig.finance.categorization.domain.RuleCategoryCatalog;
import com.xroig.finance.shared.domain.TransactionType;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adapter for {@link RuleCategoryCatalog}: reads a rule's target category (type + owning
 * account) and resolves the fallback category ("Otros gastos"/"Otros ingresos") of a type,
 * from the categories store. The fallback is matched by type and name (case-insensitive),
 * mirroring the legacy behaviour.
 */
@Component
public class RuleCategoryCatalogAdapter implements RuleCategoryCatalog {

    private final CategoryJpaRepository categories;

    public RuleCategoryCatalogAdapter(CategoryJpaRepository categories) {
        this.categories = categories;
    }

    @Override
    public Optional<RuleCategory> find(CategoryId id) {
        return categories.findById(id.value()).map(this::toRuleCategory);
    }

    @Override
    public Optional<CategoryId> fallbackFor(TransactionType type) {
        String name = type == TransactionType.EXPENSE ? "Otros gastos" : "Otros ingresos";
        return categories.findAll().stream()
                .filter(c -> c.getType() == type && c.getName().equalsIgnoreCase(name))
                .findFirst()
                .map(c -> new CategoryId(c.getId()));
    }

    private RuleCategory toRuleCategory(CategoryJpaEntity entity) {
        AccountId accountId = entity.getAccount() == null ? null : new AccountId(entity.getAccount().getId());
        return new RuleCategory(new CategoryId(entity.getId()), entity.getType(), accountId);
    }
}
