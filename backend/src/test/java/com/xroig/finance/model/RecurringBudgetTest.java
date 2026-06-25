package com.xroig.finance.model;

import com.xroig.finance.shared.domain.TransactionType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.xroig.finance.Fixtures.amount;
import static com.xroig.finance.Fixtures.category;
import static com.xroig.finance.Fixtures.eur;
import static com.xroig.finance.Fixtures.recurrence;
import static org.assertj.core.api.Assertions.assertThat;

class RecurringBudgetTest {

    private final Category cat = category(1, "Hipoteca", TransactionType.EXPENSE);

    @Test
    void appliesToMonth_readsTheBitmask() {
        // Bits for January (1) and March (3): 0b101 = 5.
        RecurringBudget r = recurrence(cat, 0b101, true, amount(eur("100"), "2026-01"));
        assertThat(r.appliesToMonth(1)).isTrue();
        assertThat(r.appliesToMonth(2)).isFalse();
        assertThat(r.appliesToMonth(3)).isTrue();
        assertThat(r.appliesToMonth(12)).isFalse();
    }

    @Test
    void amountAt_picksTheMostRecentValidoDesdeOnOrBeforeTheDate() {
        RecurringBudget r = recurrence(cat, 0b1, true,
                amount(eur("100"), "2026-01"),
                amount(eur("120"), "2026-06"));

        assertThat(r.amountAt(LocalDate.of(2026, 3, 1))).isEqualByComparingTo("100");
        assertThat(r.amountAt(LocalDate.of(2026, 6, 1))).isEqualByComparingTo("120"); // equal date counts
        assertThat(r.amountAt(LocalDate.of(2026, 8, 1))).isEqualByComparingTo("120");
    }

    @Test
    void amountAt_isNullBeforeTheFirstValidoDesde() {
        RecurringBudget r = recurrence(cat, 0b1, true, amount(eur("100"), "2026-01"));
        assertThat(r.amountAt(LocalDate.of(2025, 12, 1))).isNull();
    }

    @Test
    void amountAt_isNullWhenThereAreNoAmounts() {
        RecurringBudget r = recurrence(cat, 0b1, true);
        assertThat(r.amountAt(LocalDate.of(2026, 6, 1))).isNull();
    }
}
