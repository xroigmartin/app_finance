package com.xroig.finance.service;

import com.xroig.finance.dto.BudgetDtos.AnnualBudget;
import com.xroig.finance.dto.BudgetDtos.AnnualRow;
import com.xroig.finance.model.Account;
import com.xroig.finance.model.Budget;
import com.xroig.finance.model.Category;
import com.xroig.finance.model.RecurringBudget;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.repository.BudgetRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.RecurringBudgetRepository;
import com.xroig.finance.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static com.xroig.finance.Fixtures.account;
import static com.xroig.finance.Fixtures.amount;
import static com.xroig.finance.Fixtures.budget;
import static com.xroig.finance.Fixtures.category;
import static com.xroig.finance.Fixtures.eur;
import static com.xroig.finance.Fixtures.recurrence;
import static com.xroig.finance.Fixtures.subcategory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Service tests for {@link BudgetService#annual} with mocked repositories
 * (stage E2c): manual budget vs recurrence planned value, real movement sums,
 * parent/child aggregation, account vs aggregate scope, and row split/sort.
 */
@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    private static final int YEAR = 2024;

    @Mock private BudgetRepository budgetRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private RecurringBudgetRepository recurringBudgetRepository;
    @InjectMocks private BudgetService service;

    private final Account corriente = account(1, "Corriente");

    /** A real-movement sum row as returned by {@code sumByExactCategoryAndMonthOfYear}. */
    private static Object[] sumRow(long categoryId, int month, String amount) {
        return new Object[] {categoryId, month, eur(amount)};
    }

    /** Typed list of sum rows (avoids {@code List.of(Object[])} inferring List<Object>). */
    private static List<Object[]> sums(Object[]... rows) {
        return List.of(rows);
    }

    private static AnnualRow rowFor(List<AnnualRow> rows, long categoryId) {
        return rows.stream().filter(r -> r.categoryId() == categoryId).findFirst().orElseThrow();
    }

    // ---------- leaf: planned from manual budget, real from sums ----------

    @Test
    void leafCategory_plannedFromManualBudgetAndActualFromSums() {
        Category comida = category(10, "Comida", TransactionType.EXPENSE, corriente);
        when(categoryRepository.findVisibleForAccount(1L)).thenReturn(List.of(comida));
        when(budgetRepository.findByAccountIdAndYear(1L, YEAR))
                .thenReturn(List.of(budget(500L, corriente, comida, YEAR, 3, eur("100"))));
        when(recurringBudgetRepository.findActiveByAccountWithAmounts(1L)).thenReturn(List.of());
        when(transactionRepository.sumByExactCategoryAndMonthOfYear(YEAR, 1L))
                .thenReturn(sums(sumRow(10, 3, "50")));

        AnnualBudget result = service.annual(YEAR, 1L);

        AnnualRow row = rowFor(result.expense(), 10);
        assertThat(row.editable()).isTrue();
        assertThat(row.months().get(2).budget()).isEqualByComparingTo("100");
        assertThat(row.months().get(2).actual()).isEqualByComparingTo("50");
        assertThat(row.months().get(2).budgetId()).isEqualTo(500L);
        assertThat(row.months().get(0).budget()).isEqualByComparingTo("0");
        assertThat(row.months().get(0).budgetId()).isNull();
    }

    // ---------- leaf: recurrence feeds planned when no manual budget ----------

    @Test
    void recurrence_fillsPlannedWhenNoManualBudget() {
        Category comunidad = category(10, "Comunidad", TransactionType.EXPENSE, corriente);
        RecurringBudget rec = recurrence(comunidad, 0b0000_0000_0100, true, // month 3 active
                amount(eur("80"), "2024-01"));
        when(categoryRepository.findVisibleForAccount(1L)).thenReturn(List.of(comunidad));
        when(budgetRepository.findByAccountIdAndYear(1L, YEAR)).thenReturn(List.of());
        when(recurringBudgetRepository.findActiveByAccountWithAmounts(1L)).thenReturn(List.of(rec));
        when(transactionRepository.sumByExactCategoryAndMonthOfYear(YEAR, 1L)).thenReturn(List.of());

        AnnualRow row = rowFor(service.annual(YEAR, 1L).expense(), 10);

        assertThat(row.months().get(2).budget()).isEqualByComparingTo("80");
        assertThat(row.months().get(2).budgetId()).isNull();
        assertThat(row.months().get(1).budget()).isEqualByComparingTo("0"); // month 2 not active
    }

    @Test
    void manualBudgetOverridesRecurrence() {
        Category comunidad = category(10, "Comunidad", TransactionType.EXPENSE, corriente);
        RecurringBudget rec = recurrence(comunidad, 0b0000_0000_0100, true,
                amount(eur("80"), "2024-01"));
        when(categoryRepository.findVisibleForAccount(1L)).thenReturn(List.of(comunidad));
        when(budgetRepository.findByAccountIdAndYear(1L, YEAR))
                .thenReturn(List.of(budget(500L, corriente, comunidad, YEAR, 3, eur("120"))));
        when(recurringBudgetRepository.findActiveByAccountWithAmounts(1L)).thenReturn(List.of(rec));
        when(transactionRepository.sumByExactCategoryAndMonthOfYear(YEAR, 1L)).thenReturn(List.of());

        AnnualRow row = rowFor(service.annual(YEAR, 1L).expense(), 10);

        assertThat(row.months().get(2).budget()).isEqualByComparingTo("120");
    }

    // ---------- parent aggregates own + children ----------

    @Test
    void parentRow_aggregatesOwnAndChildrenAndNestsChildren() {
        Category parent = category(10, "Hogar", TransactionType.EXPENSE, corriente);
        Category child = subcategory(11, "Luz", parent);
        when(categoryRepository.findVisibleForAccount(1L)).thenReturn(List.of(parent, child));
        when(budgetRepository.findByAccountIdAndYear(1L, YEAR))
                .thenReturn(List.of(budget(500L, corriente, child, YEAR, 1, eur("50"))));
        when(recurringBudgetRepository.findActiveByAccountWithAmounts(1L)).thenReturn(List.of());
        when(transactionRepository.sumByExactCategoryAndMonthOfYear(YEAR, 1L))
                .thenReturn(sums(sumRow(10, 1, "10"), sumRow(11, 1, "30"))); // parent legacy + child

        AnnualBudget result = service.annual(YEAR, 1L);

        // Only the parent is at top level; the child is nested under it.
        assertThat(result.expense()).extracting(AnnualRow::categoryId).containsExactly(10L);
        AnnualRow parentRow = result.expense().get(0);
        assertThat(parentRow.editable()).isFalse();
        assertThat(parentRow.months().get(0).budget()).isEqualByComparingTo("50"); // 0 own + 50 child
        assertThat(parentRow.months().get(0).actual()).isEqualByComparingTo("40"); // 10 own + 30 child
        assertThat(parentRow.children()).extracting(AnnualRow::categoryId).containsExactly(11L);
        AnnualRow childRow = parentRow.children().get(0);
        assertThat(childRow.editable()).isTrue();
        assertThat(childRow.months().get(0).budget()).isEqualByComparingTo("50");
        assertThat(childRow.months().get(0).actual()).isEqualByComparingTo("30");
    }

    // ---------- aggregate scope (accountId null) ----------

    @Test
    void aggregateScope_usesFindAllAndCarriesNoBudgetIds() {
        Category comida = category(10, "Comida", TransactionType.EXPENSE);
        when(categoryRepository.findAll()).thenReturn(List.of(comida));
        when(budgetRepository.findByYear(YEAR))
                .thenReturn(List.of(budget(500L, corriente, comida, YEAR, 3, eur("100"))));
        when(recurringBudgetRepository.findAllActiveWithAmounts()).thenReturn(List.of());
        when(transactionRepository.sumByExactCategoryAndMonthOfYear(YEAR, null))
                .thenReturn(sums(sumRow(10, 3, "50")));

        AnnualBudget result = service.annual(YEAR, null);

        assertThat(result.accountId()).isNull();
        AnnualRow row = rowFor(result.expense(), 10);
        assertThat(row.months().get(2).budget()).isEqualByComparingTo("100");
        assertThat(row.months().get(2).actual()).isEqualByComparingTo("50");
        assertThat(row.months().get(2).budgetId()).isNull(); // read-only in aggregate scope
    }

    // ---------- split income/expense and sort by name ----------

    @Test
    void rowsSplitByTypeAndSortedByNameCaseInsensitive() {
        Category zeta = category(10, "Zeta", TransactionType.EXPENSE, corriente);
        Category alfa = category(11, "alfa", TransactionType.EXPENSE, corriente);
        Category nomina = category(12, "Nómina", TransactionType.INCOME, corriente);
        when(categoryRepository.findVisibleForAccount(1L)).thenReturn(List.of(zeta, alfa, nomina));
        when(budgetRepository.findByAccountIdAndYear(1L, YEAR)).thenReturn(List.of());
        when(recurringBudgetRepository.findActiveByAccountWithAmounts(1L)).thenReturn(List.of());
        when(transactionRepository.sumByExactCategoryAndMonthOfYear(YEAR, 1L)).thenReturn(List.of());

        AnnualBudget result = service.annual(YEAR, 1L);

        assertThat(result.expense()).extracting(AnnualRow::category).containsExactly("alfa", "Zeta");
        assertThat(result.income()).extracting(AnnualRow::category).containsExactly("Nómina");
    }
}
