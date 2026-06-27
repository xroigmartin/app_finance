package com.xroig.finance.budgets.application;

import com.xroig.finance.budgets.application.port.DeleteRecurrence;
import com.xroig.finance.budgets.application.port.FindRecurrence;
import com.xroig.finance.budgets.application.port.UpsertRecurrence;
import com.xroig.finance.budgets.domain.CategoryCatalog;
import com.xroig.finance.budgets.domain.CategoryCatalog.CategoryBudgetInfo;
import com.xroig.finance.budgets.domain.MonthsMask;
import com.xroig.finance.budgets.domain.RecurrenceAmount;
import com.xroig.finance.budgets.domain.RecurringBudget;
import com.xroig.finance.budgets.domain.RecurringBudgetRepository;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Application service for the recurrence sub-resource. Orchestrates the {@link RecurringBudget}
 * aggregate and the outbound ports, preserving the legacy guard order on upsert: category
 * existence (404), then global (400), then has-subcategories (400), then the month mask and
 * the amount parsing (400). The amount history reconciliation is the aggregate's behavior;
 * the duplicate-effective-month rule is a domain invariant. Reads go through the query port.
 */
@Service
@Transactional
public class RecurringBudgetService implements FindRecurrence, UpsertRecurrence, DeleteRecurrence {

    private final RecurringBudgetRepository recurrences;
    private final CategoryCatalog categories;
    private final RecurringBudgetQueryPort queries;

    public RecurringBudgetService(RecurringBudgetRepository recurrences, CategoryCatalog categories,
                                  RecurringBudgetQueryPort queries) {
        this.recurrences = recurrences;
        this.categories = categories;
        this.queries = queries;
    }

    @Override
    @Transactional(readOnly = true)
    public RecurringBudgetView get(long categoryId) {
        return queries.find(new CategoryId(categoryId))
                .orElseThrow(() -> new NotFoundException("La categoría no tiene recurrencia"));
    }

    @Override
    public RecurringBudgetView upsert(long categoryId, RecurrenceCommand command) {
        CategoryId catId = new CategoryId(categoryId);
        CategoryBudgetInfo info = categories.find(catId)
                .orElseThrow(() -> new NotFoundException("Categoría no encontrada"));
        if (info.isGlobal()) {
            throw new ValidationException(
                    "Las categorías globales no admiten recurrencia; crea una subcategoría ligada a una cuenta");
        }
        if (info.hasChildren()) {
            throw new ValidationException(
                    "No se puede definir recurrencia en una categoría con subcategorías; defínela en sus subcategorías");
        }
        MonthsMask months = MonthsMask.ofMonths(command.months());
        List<RecurrenceAmount> amounts = parseAmounts(command.amounts());

        RecurringBudget recurrence = recurrences.findByCategory(catId)
                .map(existing -> {
                    existing.reconcile(months, command.active(), amounts);
                    return existing;
                })
                .orElseGet(() -> RecurringBudget.create(catId, months, command.active(), amounts));
        recurrences.save(recurrence);
        return view(categoryId);
    }

    @Override
    public void delete(long categoryId) {
        recurrences.deleteByCategory(new CategoryId(categoryId));
    }

    private static List<RecurrenceAmount> parseAmounts(List<AmountInput> requested) {
        List<RecurrenceAmount> result = new ArrayList<>();
        for (AmountInput in : requested) {
            result.add(new RecurrenceAmount(Money.of(in.amount()), parseYearMonth(in.validoDesde())));
        }
        return result;
    }

    private static YearMonth parseYearMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            throw new ValidationException("Fecha de vigencia no válida (formato esperado AAAA-MM): " + value);
        }
    }

    private RecurringBudgetView view(long categoryId) {
        return queries.find(new CategoryId(categoryId)).orElseThrow(
                () -> new IllegalStateException("La recurrencia recién guardada no se pudo leer: " + categoryId));
    }
}
