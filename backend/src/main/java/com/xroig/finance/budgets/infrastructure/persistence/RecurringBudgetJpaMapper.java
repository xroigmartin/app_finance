package com.xroig.finance.budgets.infrastructure.persistence;

import com.xroig.finance.budgets.domain.MonthsMask;
import com.xroig.finance.budgets.domain.RecurrenceAmount;
import com.xroig.finance.budgets.domain.RecurringBudget;
import com.xroig.finance.budgets.domain.RecurringBudgetId;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.shared.domain.Money;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Translates between the pure {@link RecurringBudget} aggregate and its
 * {@link RecurringBudgetJpaEntity}.
 *
 * <p>On write the amount history is reconciled <b>in place</b> ({@link #applyTo}) keyed by the
 * effective month: kept rows are updated, new ones added and dropped ones removed via
 * {@code orphanRemoval}. Clearing and reinserting instead would make Hibernate insert a row
 * with the same {@code (recurring_budget_id, valido_desde)} before deleting the old one
 * (inserts flush before deletes), violating {@code uq_amount_vigencia} — the exact case of
 * editing an amount while keeping its effective month. The domain models the desired state;
 * this mapper makes the persistence transition collision-free.
 */
@Component
public class RecurringBudgetJpaMapper {

    public RecurringBudget toDomain(RecurringBudgetJpaEntity entity) {
        java.util.List<RecurrenceAmount> amounts = entity.getAmounts().stream()
                .map(a -> new RecurrenceAmount(Money.of(a.getAmount()), YearMonth.from(a.getValidoDesde())))
                .toList();
        return RecurringBudget.rehydrate(new RecurringBudgetId(entity.getId()),
                new CategoryId(entity.getCategory().getId()),
                MonthsMask.ofBitmask(entity.getMonths()), entity.isActive(), amounts);
    }

    /** Copies the aggregate state onto the (possibly already managed) entity, reconciling amounts in place. */
    public void applyTo(RecurringBudgetJpaEntity entity, RecurringBudget recurrence) {
        entity.setMonths(recurrence.months().bitmask());
        entity.setActive(recurrence.isActive());

        Map<YearMonth, RecurrenceAmountJpaEntity> existing = new HashMap<>();
        for (RecurrenceAmountJpaEntity a : entity.getAmounts()) {
            existing.put(YearMonth.from(a.getValidoDesde()), a);
        }
        Set<YearMonth> wanted = new HashSet<>();
        for (RecurrenceAmount a : recurrence.amounts()) {
            YearMonth from = a.from();
            wanted.add(from);
            RecurrenceAmountJpaEntity row = existing.get(from);
            if (row != null) {
                row.setAmount(a.amount().amount());
            } else {
                RecurrenceAmountJpaEntity added = new RecurrenceAmountJpaEntity();
                added.setRecurringBudget(entity);
                added.setAmount(a.amount().amount());
                added.setValidoDesde(from.atDay(1));
                entity.getAmounts().add(added);
            }
        }
        entity.getAmounts().removeIf(a -> !wanted.contains(YearMonth.from(a.getValidoDesde())));
    }
}
