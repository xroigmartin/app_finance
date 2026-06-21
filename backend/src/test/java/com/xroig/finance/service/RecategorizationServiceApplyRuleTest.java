package com.xroig.finance.service;

import com.xroig.finance.model.Account;
import com.xroig.finance.model.Category;
import com.xroig.finance.model.CategoryRule;
import com.xroig.finance.model.Transaction;
import com.xroig.finance.model.TransactionType;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static com.xroig.finance.Fixtures.account;
import static com.xroig.finance.Fixtures.category;
import static com.xroig.finance.Fixtures.eur;
import static com.xroig.finance.Fixtures.expense;
import static com.xroig.finance.Fixtures.rule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service tests for {@link RecategorizationService#applyRule} with mocked
 * repositories (stage E2c). Only fallback transactions matching the rule, and
 * within the rule category's account scope, are moved.
 */
@ExtendWith(MockitoExtension.class)
class RecategorizationServiceApplyRuleTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private CategoryRepository categoryRepository;
    @InjectMocks private RecategorizationService service;

    private final Account corriente = account(1, "Corriente");
    private final Account ahorro = account(2, "Ahorro");
    private final Category otrosGastos = category(100, "Otros gastos", TransactionType.EXPENSE);
    private final LocalDate d = LocalDate.of(2024, 1, 1);

    @Test
    void movesOnlyFallbackTransactionsThatMatchTheRule() {
        Category supermercado = category(10, "Supermercado", TransactionType.EXPENSE);
        CategoryRule r = rule(1, "mercadona|lidl", supermercado);
        Transaction t1 = expense(1L, eur("30"), corriente, otrosGastos, d);
        t1.setDescription("Compra MERCADONA centro");
        Transaction t2 = expense(2L, eur("10"), corriente, otrosGastos, d);
        t2.setDescription("Pago en farmacia");
        Transaction t3 = expense(3L, eur("20"), corriente, otrosGastos, d);
        t3.setDescription("LIDL");
        when(categoryRepository.findAll()).thenReturn(List.of(otrosGastos, supermercado));
        when(transactionRepository.findByCategoryId(100L)).thenReturn(List.of(t1, t2, t3));

        int moved = service.applyRule(r);

        assertThat(moved).isEqualTo(2);
        assertThat(t1.getCategory()).isSameAs(supermercado);
        assertThat(t3.getCategory()).isSameAs(supermercado);
        assertThat(t2.getCategory()).isSameAs(otrosGastos);
        verify(transactionRepository).saveAll(List.of(t1, t2, t3));
    }

    @Test
    void returnsZeroWhenNoFallbackCategoryExists() {
        Category supermercado = category(10, "Supermercado", TransactionType.EXPENSE);
        when(categoryRepository.findAll()).thenReturn(List.of(supermercado));

        int moved = service.applyRule(rule(1, "mercadona", supermercado));

        assertThat(moved).isZero();
        verify(transactionRepository, never()).findByCategoryId(any());
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void returnsZeroWhenRuleTargetsTheFallbackItself() {
        CategoryRule r = rule(1, "mercadona", otrosGastos);
        when(categoryRepository.findAll()).thenReturn(List.of(otrosGastos));

        int moved = service.applyRule(r);

        assertThat(moved).isZero();
        verify(transactionRepository, never()).findByCategoryId(any());
    }

    @Test
    void accountScopedTargetOnlyTakesTransactionsOfThatAccount() {
        Category superCorriente = category(10, "Supermercado", TransactionType.EXPENSE, corriente);
        CategoryRule r = rule(1, "mercadona", superCorriente);
        Transaction same = expense(1L, eur("30"), corriente, otrosGastos, d);
        same.setDescription("MERCADONA");
        Transaction other = expense(2L, eur("30"), ahorro, otrosGastos, d);
        other.setDescription("MERCADONA");
        when(categoryRepository.findAll()).thenReturn(List.of(otrosGastos, superCorriente));
        when(transactionRepository.findByCategoryId(100L)).thenReturn(List.of(same, other));

        int moved = service.applyRule(r);

        assertThat(moved).isEqualTo(1);
        assertThat(same.getCategory()).isSameAs(superCorriente);
        assertThat(other.getCategory()).isSameAs(otrosGastos);
    }

    @Test
    void globalTargetTakesTransactionsRegardlessOfAccount() {
        Category supermercado = category(10, "Supermercado", TransactionType.EXPENSE);
        CategoryRule r = rule(1, "mercadona", supermercado);
        Transaction a = expense(1L, eur("30"), corriente, otrosGastos, d);
        a.setDescription("MERCADONA");
        Transaction b = expense(2L, eur("30"), ahorro, otrosGastos, d);
        b.setDescription("MERCADONA");
        when(categoryRepository.findAll()).thenReturn(List.of(otrosGastos, supermercado));
        when(transactionRepository.findByCategoryId(100L)).thenReturn(List.of(a, b));

        int moved = service.applyRule(r);

        assertThat(moved).isEqualTo(2);
    }
}
