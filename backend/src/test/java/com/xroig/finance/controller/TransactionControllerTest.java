package com.xroig.finance.controller;

import com.xroig.finance.controller.TransactionController.TransactionRequest;
import com.xroig.finance.model.Account;
import com.xroig.finance.model.Category;
import com.xroig.finance.model.Transaction;
import com.xroig.finance.model.TransactionType;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.TransactionRepository;
import com.xroig.finance.service.ImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static com.xroig.finance.Fixtures.account;
import static com.xroig.finance.Fixtures.category;
import static com.xroig.finance.Fixtures.eur;
import static com.xroig.finance.Fixtures.expense;
import static com.xroig.finance.Fixtures.income;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TransactionController} with mocked repositories
 * (stage E3b): normal create/update (account/category resolution and scope)
 * and refunds (inheritance from the original expense and its limits).
 */
@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ImportService importService;
    @InjectMocks private TransactionController controller;

    private final Account corriente = account(1, "Corriente");
    private final Account ahorro = account(2, "Ahorro");
    private final LocalDate d = LocalDate.of(2024, 3, 10);

    private TransactionRequest normal(Long accountId, Long categoryId) {
        return new TransactionRequest(d, eur("50"), "Compra", TransactionType.EXPENSE,
                accountId, categoryId, null);
    }

    private TransactionRequest refund(BigDecimal amount, Long refundOfId) {
        return new TransactionRequest(d, amount, "Devolución", TransactionType.EXPENSE,
                1L, 10L, refundOfId);
    }

    private static HttpStatus statusOf(Throwable t) {
        return (HttpStatus) ((ResponseStatusException) t).getStatusCode();
    }

    // ---------- create: normal ----------

    @Test
    void create_normalResolvesAccountAndCategory() {
        Category comida = category(10, "Comida", TransactionType.EXPENSE); // global
        when(accountRepository.findById(1L)).thenReturn(Optional.of(corriente));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(comida));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        Transaction saved = controller.create(normal(1L, 10L));

        assertThat(saved.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(saved.getAccount()).isSameAs(corriente);
        assertThat(saved.getCategory()).isSameAs(comida);
        assertThat(saved.getRefundOf()).isNull();
        assertThat(saved.getAmount()).isEqualByComparingTo("50");
        assertThat(saved.getDescription()).isEqualTo("Compra");
    }

    @Test
    void create_invalidAccountThrows400() {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.create(normal(1L, 10L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_invalidCategoryThrows400() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(corriente));
        when(categoryRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.create(normal(1L, 10L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_categoryOfAnotherAccountThrows400() {
        Category ajena = category(10, "Comida", TransactionType.EXPENSE, ahorro);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(corriente));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> controller.create(normal(1L, 10L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_categoryBoundToSameAccountIsAccepted() {
        Category propia = category(10, "Comida", TransactionType.EXPENSE, corriente);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(corriente));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(propia));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        Transaction saved = controller.create(normal(1L, 10L));

        assertThat(saved.getCategory()).isSameAs(propia);
    }

    // ---------- create: refund ----------

    @Test
    void create_refundInheritsOriginalAccountCategoryAndType() {
        Category comida = category(10, "Comida", TransactionType.EXPENSE, corriente);
        Transaction original = expense(99L, eur("100"), corriente, comida, d);
        when(transactionRepository.findById(99L)).thenReturn(Optional.of(original));
        when(transactionRepository.sumRefundedAmount(99L, null)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        Transaction saved = controller.create(refund(eur("30"), 99L));

        assertThat(saved.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(saved.getAccount()).isSameAs(corriente);
        assertThat(saved.getCategory()).isSameAs(comida);
        assertThat(saved.getRefundOf()).isSameAs(original);
        assertThat(saved.getAmount()).isEqualByComparingTo("30");
    }

    @Test
    void create_refundOfMissingOriginalThrows400() {
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.create(refund(eur("30"), 99L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_refundOfNonExpenseThrows400() {
        Category nomina = category(10, "Nómina", TransactionType.INCOME, corriente);
        Transaction original = income(99L, eur("100"), corriente, nomina, d);
        when(transactionRepository.findById(99L)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> controller.create(refund(eur("30"), 99L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_refundOfAnotherRefundThrows400() {
        Category comida = category(10, "Comida", TransactionType.EXPENSE, corriente);
        Transaction original = expense(99L, eur("100"), corriente, comida, d);
        original.setRefundOf(expense(50L, eur("100"), corriente, comida, d));
        when(transactionRepository.findById(99L)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> controller.create(refund(eur("30"), 99L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_refundExceedingPendingAmountThrows400() {
        Category comida = category(10, "Comida", TransactionType.EXPENSE, corriente);
        Transaction original = expense(99L, eur("100"), corriente, comida, d);
        when(transactionRepository.findById(99L)).thenReturn(Optional.of(original));
        when(transactionRepository.sumRefundedAmount(99L, null)).thenReturn(eur("80")); // pending 20

        assertThatThrownBy(() -> controller.create(refund(eur("30"), 99L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ---------- update ----------

    @Test
    void update_notFoundThrows404() {
        when(transactionRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.update(5L, normal(1L, 10L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void update_normalAppliesAndSaves() {
        Category comida = category(10, "Comida", TransactionType.EXPENSE);
        Transaction existing = expense(5L, eur("10"), corriente, comida, d);
        when(transactionRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(corriente));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(comida));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        Transaction updated = controller.update(5L, normal(1L, 10L));

        assertThat(updated.getAmount()).isEqualByComparingTo("50");
        assertThat(updated.getAccount()).isSameAs(corriente);
    }

    @Test
    void update_selfRefundThrows400() {
        Category comida = category(10, "Comida", TransactionType.EXPENSE, corriente);
        Transaction existing = expense(5L, eur("100"), corriente, comida, d);
        when(transactionRepository.findById(5L)).thenReturn(Optional.of(existing));

        // refundOfId points to the movement itself.
        assertThatThrownBy(() -> controller.update(5L, refund(eur("30"), 5L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ---------- delete / find / recent ----------

    @Test
    void delete_delegatesToRepository() {
        controller.delete(5L);
        verify(transactionRepository).deleteById(5L);
    }

    @Test
    void find_usesWideDefaultsWhenNoDatesGiven() {
        controller.find(null, null, null, null);

        verify(transactionRepository).search(
                LocalDate.of(1970, 1, 1), LocalDate.of(2999, 12, 31), null, null);
    }

    @Test
    void recent_delegatesToRepository() {
        controller.recent();
        verify(transactionRepository).findTop10ByOrderByDateDescIdDesc();
    }
}
