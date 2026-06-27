package com.xroig.finance.budgets.infrastructure.persistence;

import com.xroig.finance.budgets.application.RecurringBudgetQueryPort;
import com.xroig.finance.budgets.application.RecurringBudgetView;
import com.xroig.finance.budgets.application.RecurringBudgetView.AmountView;
import com.xroig.finance.budgets.domain.MonthsMask;
import com.xroig.finance.categories.domain.CategoryId;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Read-side adapter (CQRS) for the recurrence sub-resource: assembles the
 * {@link RecurringBudgetView} (months as a 1..12 list, amounts sorted by effective month and
 * carrying their persistence id) from the store, without reconstructing the aggregate.
 */
@Component
public class RecurringBudgetQueryAdapter implements RecurringBudgetQueryPort {

    private final RecurringBudgetJpaRepository jpa;

    public RecurringBudgetQueryAdapter(RecurringBudgetJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<RecurringBudgetView> find(CategoryId categoryId) {
        return jpa.findByCategoryIdWithAmounts(categoryId.value()).map(RecurringBudgetQueryAdapter::toView);
    }

    private static RecurringBudgetView toView(RecurringBudgetJpaEntity entity) {
        List<Integer> months = MonthsMask.ofBitmask(entity.getMonths()).toMonths();
        List<AmountView> amounts = entity.getAmounts().stream()
                .sorted(Comparator.comparing(RecurrenceAmountJpaEntity::getValidoDesde))
                .map(a -> new AmountView(a.getId(), a.getAmount(),
                        YearMonth.from(a.getValidoDesde()).toString()))
                .toList();
        return new RecurringBudgetView(entity.getCategory().getId(), months, entity.isActive(), amounts);
    }
}
