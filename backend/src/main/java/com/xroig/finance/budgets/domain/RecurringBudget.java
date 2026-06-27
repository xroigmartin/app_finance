package com.xroig.finance.budgets.domain;

import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.ValidationException;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Recurring planned payment attached to a leaf, account-bound category (e.g. comunidad,
 * hipoteca). It only feeds the "planned" side of the annual budget matrix; it never
 * creates real movements. Pure of any framework or persistence concern; the owning
 * category is referenced by identity ({@link CategoryId}).
 *
 * <p>Invariants kept here: at most one {@link RecurrenceAmount} per effective month (no two
 * amounts share a {@code from}). The cross-aggregate guards (the category must exist, be a
 * leaf and be account-bound) live in the application service through the {@code CategoryCatalog}
 * port. The {@link MonthsMask} VO validates the month range.
 *
 * <p>The planned amount of a month is the amount whose {@code from} is the most recent one
 * on or before that month ({@link #plannedAmount}); months outside the mask plan nothing.
 */
public class RecurringBudget {

    private final RecurringBudgetId id;
    private final CategoryId categoryId;
    private MonthsMask months;
    private boolean active;
    private final List<RecurrenceAmount> amounts = new ArrayList<>();

    private RecurringBudget(RecurringBudgetId id, CategoryId categoryId, MonthsMask months,
                            boolean active, List<RecurrenceAmount> amounts) {
        this.id = id;
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
        this.months = Objects.requireNonNull(months, "months");
        this.active = active;
        replaceAmounts(amounts);
    }

    /** Factory for a brand-new recurrence (no identity yet). */
    public static RecurringBudget create(CategoryId categoryId, MonthsMask months,
                                         boolean active, List<RecurrenceAmount> amounts) {
        return new RecurringBudget(null, categoryId, months, active, amounts);
    }

    /** Rebuilds a persisted recurrence (identity present), from the persistence adapter. */
    public static RecurringBudget rehydrate(RecurringBudgetId id, CategoryId categoryId, MonthsMask months,
                                            boolean active, List<RecurrenceAmount> amounts) {
        if (id == null) {
            throw new IllegalArgumentException("Una recurrencia rehidratada necesita identidad");
        }
        return new RecurringBudget(id, categoryId, months, active, amounts);
    }

    /** Re-applies the editable state (months, active flag and amount history), re-checking invariants. */
    public void reconcile(MonthsMask months, boolean active, List<RecurrenceAmount> amounts) {
        this.months = Objects.requireNonNull(months, "months");
        this.active = active;
        replaceAmounts(amounts);
    }

    /** Planned amount for a given calendar month, present only when that month is active. */
    public Optional<Money> plannedAmount(int year, int month) {
        if (!months.appliesToMonth(month)) {
            return Optional.empty();
        }
        return amountAt(YearMonth.of(year, month));
    }

    /** Amount in force on a month: the most recent {@code from} on or before it, if any. */
    public Optional<Money> amountAt(YearMonth at) {
        RecurrenceAmount best = null;
        for (RecurrenceAmount a : amounts) {
            if (!a.from().isAfter(at) && (best == null || a.from().isAfter(best.from()))) {
                best = a;
            }
        }
        return Optional.ofNullable(best).map(RecurrenceAmount::amount);
    }

    private void replaceAmounts(List<RecurrenceAmount> next) {
        Set<YearMonth> seen = new HashSet<>();
        for (RecurrenceAmount a : next) {
            if (!seen.add(a.from())) {
                throw new ValidationException(
                        "Hay dos importes con la misma fecha de vigencia (" + a.from() + ")");
            }
        }
        amounts.clear();
        amounts.addAll(next);
    }

    public RecurringBudgetId id() {
        return id;
    }

    public CategoryId categoryId() {
        return categoryId;
    }

    public MonthsMask months() {
        return months;
    }

    public boolean isActive() {
        return active;
    }

    public List<RecurrenceAmount> amounts() {
        return List.copyOf(amounts);
    }
}
