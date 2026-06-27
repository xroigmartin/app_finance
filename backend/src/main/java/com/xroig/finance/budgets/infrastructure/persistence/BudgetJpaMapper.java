package com.xroig.finance.budgets.infrastructure.persistence;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaRepository;
import com.xroig.finance.budgets.domain.Budget;
import com.xroig.finance.budgets.domain.BudgetId;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaRepository;
import com.xroig.finance.shared.domain.Money;
import org.springframework.stereotype.Component;

/**
 * Translates between the pure {@link Budget} aggregate and its {@link BudgetJpaEntity}.
 * On write it resolves the account and category from their ids via {@code getReferenceById};
 * on read it takes only their ids back.
 */
@Component
public class BudgetJpaMapper {

    private final AccountJpaRepository accounts;
    private final CategoryJpaRepository categories;

    public BudgetJpaMapper(AccountJpaRepository accounts, CategoryJpaRepository categories) {
        this.accounts = accounts;
        this.categories = categories;
    }

    public Budget toDomain(BudgetJpaEntity entity) {
        return Budget.rehydrate(new BudgetId(entity.getId()),
                new AccountId(entity.getAccount().getId()), new CategoryId(entity.getCategory().getId()),
                entity.getYear(), entity.getMonth(), Money.of(entity.getAmount()));
    }

    public BudgetJpaEntity toJpa(Budget budget) {
        BudgetJpaEntity entity = new BudgetJpaEntity();
        if (budget.id() != null) {
            entity.setId(budget.id().value());
        }
        entity.setAccount(accounts.getReferenceById(budget.accountId().value()));
        entity.setCategory(categories.getReferenceById(budget.categoryId().value()));
        entity.setYear(budget.year());
        entity.setMonth(budget.month());
        entity.setAmount(budget.amount().amount());
        return entity;
    }
}
