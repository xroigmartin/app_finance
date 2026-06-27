package com.xroig.finance.budgets.infrastructure.persistence;

import com.xroig.finance.budgets.domain.RecurringBudget;
import com.xroig.finance.budgets.domain.RecurringBudgetRepository;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Command-side persistence adapter implementing the {@link RecurringBudgetRepository} port over
 * JPA. On {@code save} it loads the existing entity (1:1 by category) or creates one resolving
 * the category via {@code getReferenceById}, then lets the mapper reconcile the amount history
 * in place so editing an amount on the same effective month never collides with
 * {@code uq_amount_vigencia}.
 */
@Component
public class RecurringBudgetPersistenceAdapter implements RecurringBudgetRepository {

    private final RecurringBudgetJpaRepository jpa;
    private final RecurringBudgetJpaMapper mapper;
    private final CategoryJpaRepository categories;

    public RecurringBudgetPersistenceAdapter(RecurringBudgetJpaRepository jpa, RecurringBudgetJpaMapper mapper,
                                             CategoryJpaRepository categories) {
        this.jpa = jpa;
        this.mapper = mapper;
        this.categories = categories;
    }

    @Override
    public Optional<RecurringBudget> findByCategory(CategoryId categoryId) {
        return jpa.findByCategoryIdWithAmounts(categoryId.value()).map(mapper::toDomain);
    }

    @Override
    public boolean existsByCategory(CategoryId categoryId) {
        return jpa.existsByCategoryId(categoryId.value());
    }

    @Override
    public RecurringBudget save(RecurringBudget recurrence) {
        Long categoryId = recurrence.categoryId().value();
        RecurringBudgetJpaEntity entity = jpa.findByCategoryIdWithAmounts(categoryId)
                .orElseGet(() -> {
                    RecurringBudgetJpaEntity created = new RecurringBudgetJpaEntity();
                    created.setCategory(categories.getReferenceById(categoryId));
                    return created;
                });
        mapper.applyTo(entity, recurrence);
        return mapper.toDomain(jpa.save(entity));
    }

    @Override
    public void deleteByCategory(CategoryId categoryId) {
        jpa.findByCategoryIdWithAmounts(categoryId.value()).ifPresent(jpa::delete);
    }

    @Override
    public List<RecurringBudget> findActiveByAccount(Long accountId) {
        List<RecurringBudgetJpaEntity> entities = accountId != null
                ? jpa.findActiveByAccountWithAmounts(accountId)
                : jpa.findAllActiveWithAmounts();
        return entities.stream().map(mapper::toDomain).toList();
    }
}
