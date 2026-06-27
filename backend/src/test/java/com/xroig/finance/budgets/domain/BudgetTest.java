package com.xroig.finance.budgets.domain;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure domain tests for the {@link Budget} aggregate (stage H5a): the month-range and
 * positive-amount invariants, the slot helper, copy and rehydrate.
 */
class BudgetTest {

    private static final AccountId ACCOUNT = new AccountId(1L);
    private static final CategoryId CATEGORY = new CategoryId(10L);

    @Test
    void create_buildsBudgetWithoutIdentity() {
        Budget budget = Budget.create(ACCOUNT, CATEGORY, 2024, 3, Money.of("100"));

        assertThat(budget.id()).isNull();
        assertThat(budget.accountId()).isEqualTo(ACCOUNT);
        assertThat(budget.categoryId()).isEqualTo(CATEGORY);
        assertThat(budget.year()).isEqualTo(2024);
        assertThat(budget.month()).isEqualTo(3);
        assertThat(budget.amount()).isEqualTo(Money.of("100"));
    }

    @Test
    void create_rejectsMonthOutOfRange() {
        assertThatThrownBy(() -> Budget.create(ACCOUNT, CATEGORY, 2024, 0, Money.of("100")))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Budget.create(ACCOUNT, CATEGORY, 2024, 13, Money.of("100")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> Budget.create(ACCOUNT, CATEGORY, 2024, 3, Money.zero()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Budget.create(ACCOUNT, CATEGORY, 2024, 3, Money.of("-5")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rehydrate_requiresIdentity() {
        assertThatThrownBy(() -> Budget.rehydrate(null, ACCOUNT, CATEGORY, 2024, 3, Money.of("100")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reassign_reAppliesFieldsAndRechecksInvariants() {
        Budget budget = Budget.rehydrate(new BudgetId(5L), ACCOUNT, CATEGORY, 2024, 3, Money.of("100"));

        budget.reassign(new AccountId(2L), new CategoryId(11L), 2025, 6, Money.of("150"));

        assertThat(budget.id()).isEqualTo(new BudgetId(5L));
        assertThat(budget.accountId()).isEqualTo(new AccountId(2L));
        assertThat(budget.month()).isEqualTo(6);
        assertThat(budget.amount()).isEqualTo(Money.of("150"));
        assertThatThrownBy(() -> budget.reassign(ACCOUNT, CATEGORY, 2024, 13, Money.of("1")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void isAt_matchesTheExactSlot() {
        Budget budget = Budget.rehydrate(new BudgetId(5L), ACCOUNT, CATEGORY, 2024, 3, Money.of("100"));

        assertThat(budget.isAt(ACCOUNT, CATEGORY, 2024, 3)).isTrue();
        assertThat(budget.isAt(ACCOUNT, CATEGORY, 2024, 4)).isFalse();
        assertThat(budget.isAt(new AccountId(2L), CATEGORY, 2024, 3)).isFalse();
    }

    @Test
    void copyTo_keepsAccountCategoryAmountWithoutIdentity() {
        Budget budget = Budget.rehydrate(new BudgetId(5L), ACCOUNT, CATEGORY, 2024, 1, Money.of("100"));

        Budget copy = budget.copyTo(2024, 2);

        assertThat(copy.id()).isNull();
        assertThat(copy.accountId()).isEqualTo(ACCOUNT);
        assertThat(copy.categoryId()).isEqualTo(CATEGORY);
        assertThat(copy.year()).isEqualTo(2024);
        assertThat(copy.month()).isEqualTo(2);
        assertThat(copy.amount()).isEqualTo(Money.of("100"));
    }
}
