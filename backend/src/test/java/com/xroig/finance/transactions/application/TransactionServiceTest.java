package com.xroig.finance.transactions.application;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.shared.domain.ValidationException;
import com.xroig.finance.transactions.application.port.CreateTransaction.TransactionCommand;
import com.xroig.finance.transactions.domain.AccountExistence;
import com.xroig.finance.transactions.domain.CategoryCatalog;
import com.xroig.finance.transactions.domain.CategoryCatalog.CategoryDescriptor;
import com.xroig.finance.transactions.domain.Transaction;
import com.xroig.finance.transactions.domain.TransactionId;
import com.xroig.finance.transactions.domain.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application-service tests for the transactions use cases with the outbound ports
 * mocked. They reproduce the branch logic the legacy {@code TransactionControllerTest}
 * pinned (account/category resolution and scope, refund resolution and the self-refund
 * check) — the refund invariants themselves live in {@code TransactionTest}.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final LocalDate D = LocalDate.of(2024, 3, 10);
    private static final AccountId ACC = new AccountId(1L);
    private static final CategoryId CAT = new CategoryId(10L);
    private static final TransactionView ANY_VIEW = new TransactionView(100L, D, BigDecimal.TEN, "x",
            TransactionType.EXPENSE, null, null, null);

    @Mock private TransactionRepository transactions;
    @Mock private AccountExistence accounts;
    @Mock private CategoryCatalog categories;
    @Mock private TransactionQueryPort queries;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactions, accounts, categories, queries);
    }

    private TransactionCommand normal(Long accountId, Long categoryId) {
        return new TransactionCommand(D, new BigDecimal("50"), "Compra", TransactionType.EXPENSE,
                accountId, categoryId, null);
    }

    private TransactionCommand refund(String amount, Long refundOfId) {
        return new TransactionCommand(D, new BigDecimal(amount), "Devolución", TransactionType.EXPENSE,
                1L, 10L, refundOfId);
    }

    private static Transaction expense(long id, String amount) {
        return Transaction.rehydrate(new TransactionId(id), D, Money.of(amount), null,
                TransactionType.EXPENSE, ACC, CAT, null);
    }

    private void stubSaveAndView() {
        when(transactions.save(any())).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            return Transaction.rehydrate(new TransactionId(100L), t.date(), t.amount(), t.description(),
                    t.type(), t.accountId(), t.categoryId(), t.refundOfId());
        });
        when(queries.findById(new TransactionId(100L))).thenReturn(Optional.of(ANY_VIEW));
    }

    private Transaction captureSaved() {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactions).save(captor.capture());
        return captor.getValue();
    }

    // ---------- reads ----------

    @Test
    void search_delegatesToQueryPort() {
        when(queries.search(D, D, 1L, 10L)).thenReturn(java.util.List.of(ANY_VIEW));
        assertThat(service.search(D, D, 1L, 10L)).containsExactly(ANY_VIEW);
    }

    @Test
    void recent_delegatesToQueryPort() {
        when(queries.recent()).thenReturn(java.util.List.of(ANY_VIEW));
        assertThat(service.recent()).containsExactly(ANY_VIEW);
    }

    // ---------- create: normal ----------

    @Test
    void create_normalResolvesAccountAndCategory() {
        when(accounts.exists(ACC)).thenReturn(true);
        when(categories.find(CAT)).thenReturn(Optional.of(new CategoryDescriptor(CAT, null))); // global
        stubSaveAndView();

        service.create(normal(1L, 10L));

        Transaction saved = captureSaved();
        assertThat(saved.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(saved.accountId()).isEqualTo(ACC);
        assertThat(saved.categoryId()).isEqualTo(CAT);
        assertThat(saved.isRefund()).isFalse();
    }

    @Test
    void create_invalidAccountThrows() {
        when(accounts.exists(ACC)).thenReturn(false);

        assertThatThrownBy(() -> service.create(normal(1L, 10L)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Cuenta no válida");
        verify(transactions, never()).save(any());
    }

    @Test
    void create_invalidCategoryThrows() {
        when(accounts.exists(ACC)).thenReturn(true);
        when(categories.find(CAT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(normal(1L, 10L)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Categoría no válida");
    }

    @Test
    void create_categoryOfAnotherAccountThrows() {
        when(accounts.exists(ACC)).thenReturn(true);
        when(categories.find(CAT)).thenReturn(Optional.of(new CategoryDescriptor(CAT, new AccountId(2L))));

        assertThatThrownBy(() -> service.create(normal(1L, 10L)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("otra cuenta");
    }

    @Test
    void create_categoryBoundToSameAccountIsAccepted() {
        when(accounts.exists(ACC)).thenReturn(true);
        when(categories.find(CAT)).thenReturn(Optional.of(new CategoryDescriptor(CAT, ACC)));
        stubSaveAndView();

        service.create(normal(1L, 10L));

        assertThat(captureSaved().categoryId()).isEqualTo(CAT);
    }

    // ---------- create: refund ----------

    @Test
    void create_refundInheritsOriginalAccountCategoryAndType() {
        when(transactions.findById(new TransactionId(99L))).thenReturn(Optional.of(expense(99, "100")));
        when(transactions.refundedAmountFor(new TransactionId(99L), null)).thenReturn(Money.zero());
        stubSaveAndView();

        service.create(refund("30", 99L));

        Transaction saved = captureSaved();
        assertThat(saved.isRefund()).isTrue();
        assertThat(saved.refundOfId()).isEqualTo(new TransactionId(99L));
        assertThat(saved.accountId()).isEqualTo(ACC);
        assertThat(saved.amount()).isEqualTo(Money.of("30"));
    }

    @Test
    void create_refundOfMissingOriginalThrows() {
        when(transactions.findById(new TransactionId(99L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(refund("30", 99L)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("no existe");
    }

    @Test
    void create_refundExceedingPendingThrows() {
        when(transactions.findById(new TransactionId(99L))).thenReturn(Optional.of(expense(99, "100")));
        when(transactions.refundedAmountFor(new TransactionId(99L), null)).thenReturn(Money.of("80"));

        assertThatThrownBy(() -> service.create(refund("30", 99L)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("pendiente");
    }

    // ---------- update ----------

    @Test
    void update_notFoundThrows() {
        when(transactions.findById(new TransactionId(5L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(5L, normal(1L, 10L)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_normalAppliesAndSaves() {
        Transaction existing = expense(5, "10");
        when(transactions.findById(new TransactionId(5L))).thenReturn(Optional.of(existing));
        when(accounts.exists(ACC)).thenReturn(true);
        when(categories.find(CAT)).thenReturn(Optional.of(new CategoryDescriptor(CAT, null)));
        stubSaveAndView();

        service.update(5L, normal(1L, 10L));

        assertThat(captureSaved().amount()).isEqualTo(Money.of("50"));
    }

    @Test
    void update_toRefundConvertsAndSaves() {
        Transaction existing = Transaction.rehydrate(new TransactionId(5L), D, Money.of("10"), null,
                TransactionType.INCOME, new AccountId(2L), new CategoryId(20L), null);
        when(transactions.findById(new TransactionId(5L))).thenReturn(Optional.of(existing));
        when(transactions.findById(new TransactionId(99L))).thenReturn(Optional.of(expense(99, "100")));
        when(transactions.refundedAmountFor(new TransactionId(99L), new TransactionId(5L)))
                .thenReturn(Money.zero());
        stubSaveAndView();

        service.update(5L, refund("30", 99L));

        Transaction saved = captureSaved();
        assertThat(saved.isRefund()).isTrue();
        assertThat(saved.refundOfId()).isEqualTo(new TransactionId(99L));
        assertThat(saved.accountId()).isEqualTo(ACC);
    }

    @Test
    void create_whenSavedViewCannotBeRead_failsLoudly() {
        when(accounts.exists(ACC)).thenReturn(true);
        when(categories.find(CAT)).thenReturn(Optional.of(new CategoryDescriptor(CAT, null)));
        when(transactions.save(any())).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            return Transaction.rehydrate(new TransactionId(100L), t.date(), t.amount(), t.description(),
                    t.type(), t.accountId(), t.categoryId(), t.refundOfId());
        });
        when(queries.findById(new TransactionId(100L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(normal(1L, 10L)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void update_selfRefundThrows() {
        Transaction existing = expense(5, "100");
        when(transactions.findById(new TransactionId(5L))).thenReturn(Optional.of(existing));

        // refundOfId points to the movement itself.
        assertThatThrownBy(() -> service.update(5L, refund("30", 5L)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("su propia devolución");
    }

    // ---------- delete ----------

    @Test
    void delete_delegatesToRepository() {
        service.delete(5L);
        verify(transactions).deleteById(new TransactionId(5L));
    }
}
