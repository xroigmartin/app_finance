package com.xroig.finance.budgets.domain;

import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain tests for the {@link RecurringBudget} aggregate: the planned-amount behavior
 * (mask + effective-dated amounts) and the reconciliation with its at-most-one-amount-per-month
 * invariant.
 */
class RecurringBudgetTest {

    private static final CategoryId CAT = new CategoryId(10L);

    private static RecurrenceAmount amount(String value, String yearMonth) {
        return new RecurrenceAmount(Money.of(value), YearMonth.parse(yearMonth));
    }

    private static RecurringBudget recurrence(MonthsMask months, RecurrenceAmount... amounts) {
        return RecurringBudget.create(CAT, months, true, List.of(amounts));
    }

    @Test
    void amountAt_picksTheMostRecentFromOnOrBeforeTheMonth() {
        RecurringBudget r = recurrence(MonthsMask.ofMonths(List.of(1)),
                amount("100", "2026-01"), amount("120", "2026-06"));

        assertThat(r.amountAt(YearMonth.parse("2026-03"))).get().isEqualTo(Money.of("100"));
        assertThat(r.amountAt(YearMonth.parse("2026-06"))).get().isEqualTo(Money.of("120")); // equal counts
        assertThat(r.amountAt(YearMonth.parse("2026-08"))).get().isEqualTo(Money.of("120"));
    }

    @Test
    void amountAt_isEmptyBeforeTheFirstFromAndWhenThereAreNoAmounts() {
        assertThat(recurrence(MonthsMask.ofMonths(List.of(1)), amount("100", "2026-01"))
                .amountAt(YearMonth.parse("2025-12"))).isEmpty();
        assertThat(recurrence(MonthsMask.ofMonths(List.of(1))).amountAt(YearMonth.parse("2026-06"))).isEmpty();
    }

    @Test
    void plannedAmount_isPresentOnlyForActiveMonthsWithAnAmountInForce() {
        RecurringBudget r = recurrence(MonthsMask.ofMonths(List.of(3)), amount("80", "2026-01"));

        assertThat(r.plannedAmount(2026, 3)).get().isEqualTo(Money.of("80")); // active + in force
        assertThat(r.plannedAmount(2026, 2)).isEmpty();                       // month not active
        assertThat(r.plannedAmount(2025, 3)).isEmpty();                       // before the first amount
    }

    @Test
    void reconcile_replacesMonthsActiveAndAmounts() {
        RecurringBudget r = recurrence(MonthsMask.ofMonths(List.of(1)),
                amount("100", "2026-01"), amount("200", "2026-06"));

        // 2026-01 updated, 2026-06 dropped, 2026-09 added; mask and active flag replaced.
        r.reconcile(MonthsMask.ofMonths(List.of(2, 5)), false,
                List.of(amount("150", "2026-01"), amount("300", "2026-09")));

        assertThat(r.isActive()).isFalse();
        assertThat(r.months().toMonths()).containsExactly(2, 5);
        assertThat(r.amounts()).extracting(a -> a.from().toString())
                .containsExactlyInAnyOrder("2026-01", "2026-09");
        assertThat(r.amountAt(YearMonth.parse("2026-01"))).get().isEqualTo(Money.of("150"));
        assertThat(r.amountAt(YearMonth.parse("2026-09"))).get().isEqualTo(Money.of("300"));
    }

    @Test
    void rejectsTwoAmountsWithTheSameEffectiveMonth() {
        assertThatThrownBy(() -> recurrence(MonthsMask.ofMonths(List.of(1)),
                amount("100", "2026-01"), amount("120", "2026-01")))
                .isInstanceOf(ValidationException.class);

        RecurringBudget r = recurrence(MonthsMask.ofMonths(List.of(1)), amount("100", "2026-01"));
        assertThatThrownBy(() -> r.reconcile(MonthsMask.ofMonths(List.of(1)), true,
                List.of(amount("100", "2026-02"), amount("120", "2026-02"))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> amount("0", "2026-01")).isInstanceOf(ValidationException.class);
    }

    @Test
    void recurrenceAmount_rejectsNullAmount() {
        assertThatThrownBy(() -> new RecurrenceAmount(null, YearMonth.parse("2026-01")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rehydrate_requiresIdentity() {
        assertThatThrownBy(() -> RecurringBudget.rehydrate(null, CAT,
                MonthsMask.ofMonths(List.of(1)), true, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recurringBudgetId_rejectsNullValue() {
        assertThatThrownBy(() -> new RecurringBudgetId(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
