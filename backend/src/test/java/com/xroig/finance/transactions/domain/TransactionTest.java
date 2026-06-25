package com.xroig.finance.transactions.domain;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure domain tests for the {@link Transaction} aggregate: the refund invariant that
 * used to live in {@code TransactionController.applyRefund} (inheritance from the
 * original expense and its limits) plus the basic field invariants.
 */
class TransactionTest {

    private static final LocalDate D = LocalDate.of(2024, 3, 10);
    private static final AccountId ACC = new AccountId(1L);
    private static final CategoryId CAT = new CategoryId(10L);

    private static Transaction expense(long id, String amount) {
        return Transaction.rehydrate(new TransactionId(id), D, Money.of(amount), null,
                TransactionType.EXPENSE, ACC, CAT, null);
    }

    @Test
    void record_buildsANormalMovement() {
        Transaction t = Transaction.record(D, Money.of("50"), "Compra", TransactionType.EXPENSE, ACC, CAT);

        assertThat(t.id()).isNull();
        assertThat(t.isRefund()).isFalse();
        assertThat(t.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(t.amount()).isEqualTo(Money.of("50"));
    }

    @Test
    void record_rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> Transaction.record(D, Money.zero(), null, TransactionType.EXPENSE, ACC, CAT))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("positivo");
        assertThatThrownBy(() -> Transaction.record(D, Money.of("-5"), null, TransactionType.EXPENSE, ACC, CAT))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void record_rejectsNullAmount() {
        assertThatThrownBy(() -> Transaction.record(D, null, null, TransactionType.EXPENSE, ACC, CAT))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("positivo");
    }

    @Test
    void record_rejectsNullDate() {
        assertThatThrownBy(() -> Transaction.record(null, Money.of("5"), null, TransactionType.EXPENSE, ACC, CAT))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("fecha");
    }

    @Test
    void refundOf_inheritsOriginalTypeAccountCategoryAndLinksIt() {
        Transaction original = expense(99, "100");

        Transaction refund = Transaction.refundOf(D, Money.of("30"), "Devolución", original, Money.zero());

        assertThat(refund.isRefund()).isTrue();
        assertThat(refund.refundOfId()).isEqualTo(new TransactionId(99L));
        assertThat(refund.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(refund.accountId()).isEqualTo(ACC);
        assertThat(refund.categoryId()).isEqualTo(CAT);
        assertThat(refund.amount()).isEqualTo(Money.of("30"));
    }

    @Test
    void refundOf_rejectsRefundingAnIncome() {
        Transaction income = Transaction.rehydrate(new TransactionId(99L), D, Money.of("100"), null,
                TransactionType.INCOME, ACC, CAT, null);

        assertThatThrownBy(() -> Transaction.refundOf(D, Money.of("30"), null, income, Money.zero()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("devoluciones de gastos");
    }

    @Test
    void refundOf_rejectsRefundingAnotherRefund() {
        Transaction aRefund = Transaction.rehydrate(new TransactionId(99L), D, Money.of("100"), null,
                TransactionType.EXPENSE, ACC, CAT, new TransactionId(50L));

        assertThatThrownBy(() -> Transaction.refundOf(D, Money.of("30"), null, aRefund, Money.zero()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("devolución de otra devolución");
    }

    @Test
    void refundOf_rejectsExceedingPendingAmount() {
        Transaction original = expense(99, "100");

        // 80 already refunded → pending 20; a 30 refund is too much.
        assertThatThrownBy(() -> Transaction.refundOf(D, Money.of("30"), null, original, Money.of("80")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("pendiente: 20.00");
    }

    @Test
    void refundOf_acceptsExactlyThePendingAmount() {
        Transaction original = expense(99, "100");

        Transaction refund = Transaction.refundOf(D, Money.of("20"), null, original, Money.of("80"));

        assertThat(refund.amount()).isEqualTo(Money.of("20"));
    }

    @Test
    void changeToRefund_convertsAnExistingMovement() {
        Transaction existing = Transaction.rehydrate(new TransactionId(5L), D, Money.of("10"), "x",
                TransactionType.INCOME, new AccountId(2L), new CategoryId(20L), null);
        Transaction original = expense(99, "100");

        existing.changeToRefund(D, Money.of("40"), "Dev", original, Money.zero());

        assertThat(existing.isRefund()).isTrue();
        assertThat(existing.refundOfId()).isEqualTo(new TransactionId(99L));
        assertThat(existing.accountId()).isEqualTo(ACC);
        assertThat(existing.type()).isEqualTo(TransactionType.EXPENSE);
    }

    @Test
    void changeToNormal_clearsTheRefundLink() {
        Transaction refund = Transaction.rehydrate(new TransactionId(5L), D, Money.of("10"), null,
                TransactionType.EXPENSE, ACC, CAT, new TransactionId(99L));

        refund.changeToNormal(D, Money.of("15"), "n", TransactionType.INCOME, new AccountId(2L), new CategoryId(20L));

        assertThat(refund.isRefund()).isFalse();
        assertThat(refund.type()).isEqualTo(TransactionType.INCOME);
        assertThat(refund.accountId()).isEqualTo(new AccountId(2L));
    }

    @Test
    void rehydrate_requiresIdentity() {
        assertThatThrownBy(() -> Transaction.rehydrate(null, D, Money.of("5"), null,
                TransactionType.EXPENSE, ACC, CAT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transactionId_rejectsNull() {
        assertThatThrownBy(() -> new TransactionId(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
