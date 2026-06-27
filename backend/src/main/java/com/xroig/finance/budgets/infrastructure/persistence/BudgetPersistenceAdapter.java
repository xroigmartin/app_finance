package com.xroig.finance.budgets.infrastructure.persistence;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.budgets.domain.Budget;
import com.xroig.finance.budgets.domain.BudgetId;
import com.xroig.finance.budgets.domain.BudgetRepository;
import com.xroig.finance.categories.domain.CategoryId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Command-side persistence adapter: implements the {@link BudgetRepository} port over JPA. */
@Component
public class BudgetPersistenceAdapter implements BudgetRepository {

    private final BudgetJpaRepository jpa;
    private final BudgetJpaMapper mapper;

    public BudgetPersistenceAdapter(BudgetJpaRepository jpa, BudgetJpaMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Optional<Budget> findById(BudgetId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Budget save(Budget budget) {
        return mapper.toDomain(jpa.save(mapper.toJpa(budget)));
    }

    @Override
    public void deleteById(BudgetId id) {
        jpa.deleteById(id.value());
    }

    @Override
    public boolean existsAt(AccountId accountId, CategoryId categoryId, int year, int month) {
        return jpa.existsByAccountIdAndCategoryIdAndYearAndMonth(
                accountId.value(), categoryId.value(), year, month);
    }

    @Override
    public List<Budget> findByYearMonth(int year, int month) {
        return jpa.findByYearAndMonth(year, month).stream().map(mapper::toDomain).toList();
    }
}
