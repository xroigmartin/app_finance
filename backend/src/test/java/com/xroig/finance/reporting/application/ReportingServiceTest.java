package com.xroig.finance.reporting.application;

import com.xroig.finance.reporting.application.AccountCatalogQuery.ReportAccount;
import com.xroig.finance.reporting.application.BudgetCatalogQuery.ReportBudget;
import com.xroig.finance.reporting.application.MovementAggregateQuery.CategoryShare;
import com.xroig.finance.shared.domain.Page;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static com.xroig.finance.Fixtures.eur;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Application tests for {@link ReportingService} with the outbound query ports mocked:
 * summary (balances, savings, deltas, yield %), byCategory, monthly series, account
 * comparison and budget status. Lenient strictness because {@code summary} fans out
 * across many query calls; each test stubs the figures it asserts on.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportingServiceTest {

    @Mock private MovementAggregateQuery movements;
    @Mock private TransferAggregateQuery transfers;
    @Mock private AccountCatalogQuery accounts;
    @Mock private BudgetCatalogQuery budgets;
    @Mock private MovementQueryPort movementQuery;
    @InjectMocks private ReportingService service;

    private final ReportAccount corriente = new ReportAccount(1L, "Corriente", "Banco", eur("1000"));
    private final ReportAccount ahorro = new ReportAccount(2L, "Ahorro", "Banco", eur("0"));

    @BeforeEach
    void noTransfersByDefault() {
        when(transfers.inUntil(any(), any())).thenReturn(BigDecimal.ZERO);
        when(transfers.outUntil(any(), any())).thenReturn(BigDecimal.ZERO);
    }

    // ---------- summary ----------

    @Test
    void summary_computesBalancesSavingsDeltasAndYieldPercents() {
        when(accounts.all()).thenReturn(List.of(corriente));
        // balanceUntil = 1000 (initial) + net + transfers(0). Net per relevant date:
        when(movements.netByAccountUntil(1L, LocalDate.of(2024, 3, 31))).thenReturn(eur("500"));
        when(movements.netByAccountUntil(1L, LocalDate.of(2024, 2, 29))).thenReturn(eur("200"));
        when(movements.netByAccountUntil(1L, LocalDate.of(2023, 12, 31))).thenReturn(eur("0"));
        when(movements.netByAccountUntil(1L, LocalDate.of(2024, 12, 31))).thenReturn(eur("800"));
        LocalDate mFrom = LocalDate.of(2024, 3, 1), mTo = LocalDate.of(2024, 3, 31);
        LocalDate yFrom = LocalDate.of(2024, 1, 1), yTo = LocalDate.of(2024, 12, 31);
        when(movements.sumByType(TransactionType.INCOME, mFrom, mTo, 1L)).thenReturn(eur("400"));
        when(movements.sumByType(TransactionType.EXPENSE, mFrom, mTo, 1L)).thenReturn(eur("100"));
        when(movements.sumByType(TransactionType.INCOME, yFrom, yTo, 1L)).thenReturn(eur("5000"));
        when(movements.sumByType(TransactionType.EXPENSE, yFrom, yTo, 1L)).thenReturn(eur("3000"));

        SummaryView s = service.summary(2024, 3, 1L);

        assertThat(s.totalBalance()).isEqualByComparingTo("1500"); // 1000 + 500
        assertThat(s.monthIncome()).isEqualByComparingTo("400");
        assertThat(s.monthExpense()).isEqualByComparingTo("100");
        assertThat(s.monthSavings()).isEqualByComparingTo("300");
        assertThat(s.yearSavings()).isEqualByComparingTo("2000");
        assertThat(s.monthBalanceDelta()).isEqualByComparingTo("300"); // 1500 - 1200
        assertThat(s.yearBalanceDelta()).isEqualByComparingTo("800");  // 1800 - 1000
        assertThat(s.monthGrowthPct()).isEqualByComparingTo("25.00");  // 300/1200
        assertThat(s.yearGrowthPct()).isEqualByComparingTo("80.00");   // 800/1000
        assertThat(s.monthSavingsYieldPct()).isEqualByComparingTo("25.00");
        assertThat(s.yearSavingsYieldPct()).isEqualByComparingTo("200.00");
        assertThat(s.accounts()).singleElement().satisfies(a -> {
            assertThat(a.id()).isEqualTo(1L);
            assertThat(a.balance()).isEqualByComparingTo("1500");
        });
    }

    @Test
    void summary_yieldPercentsAreNullWhenStartBaseNotPositive() {
        when(accounts.all()).thenReturn(List.of(ahorro)); // initial 0
        when(movements.netByAccountUntil(eq(2L), any())).thenReturn(BigDecimal.ZERO);
        when(movements.sumByType(any(), any(), any(), any())).thenReturn(eur("100"));

        SummaryView s = service.summary(2024, 3, 2L);

        assertThat(s.monthGrowthPct()).isNull();
        assertThat(s.yearGrowthPct()).isNull();
        assertThat(s.monthSavingsYieldPct()).isNull();
        assertThat(s.yearSavingsYieldPct()).isNull();
    }

    @Test
    void summary_totalBalanceFiltersBySelectedAccount() {
        when(accounts.all()).thenReturn(List.of(corriente, ahorro));
        when(movements.netByAccountUntil(eq(1L), any())).thenReturn(eur("500"));
        when(movements.netByAccountUntil(eq(2L), any())).thenReturn(eur("700"));
        when(movements.sumByType(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        SummaryView s = service.summary(2024, 3, 2L);

        // Only account 2 (initial 0 + net 700) counts towards the total, but both are listed.
        assertThat(s.totalBalance()).isEqualByComparingTo("700");
        assertThat(s.accounts()).extracting(a -> a.id()).containsExactly(1L, 2L);
    }

    // ---------- byCategory ----------

    @Test
    void expensesByCategory_mapsRowsToCategoryAmounts() {
        when(movements.shareByCategory(eq(TransactionType.EXPENSE),
                eq(LocalDate.of(2024, 3, 1)), eq(LocalDate.of(2024, 3, 31)), eq(1L)))
                .thenReturn(List.of(
                        new CategoryShare("Comida", "#ff0000", eur("120")),
                        new CategoryShare("Ocio", "#00ff00", eur("40"))));

        List<CategoryAmountView> result = service.expensesByCategory(2024, 3, 1L);

        assertThat(result).extracting(CategoryAmountView::category).containsExactly("Comida", "Ocio");
        assertThat(result.get(0).color()).isEqualTo("#ff0000");
        assertThat(result.get(0).amount()).isEqualByComparingTo("120");
    }

    @Test
    void incomeByCategory_queriesIncomeType() {
        when(movements.shareByCategory(eq(TransactionType.INCOME), any(), any(), any()))
                .thenReturn(List.of(new CategoryShare("Nómina", "#0000ff", eur("2000"))));

        List<CategoryAmountView> result = service.incomeByCategory(2024, 3, null);

        assertThat(result).singleElement().satisfies(c -> {
            assertThat(c.category()).isEqualTo("Nómina");
            assertThat(c.amount()).isEqualByComparingTo("2000");
        });
    }

    // ---------- monthly evolution ----------

    @Test
    void monthlyEvolution_buildsPointsFromOldestToNewest() {
        when(movements.sumByType(eq(TransactionType.INCOME), any(), any(), eq(1L))).thenReturn(eur("100"));
        when(movements.sumByType(eq(TransactionType.EXPENSE), any(), any(), eq(1L))).thenReturn(eur("60"));

        List<MonthlyPointView> points = service.monthlyEvolution(3, YearMonth.of(2024, 3), 1L);

        assertThat(points).extracting(MonthlyPointView::month)
                .containsExactly("2024-01", "2024-02", "2024-03");
        assertThat(points.get(0).income()).isEqualByComparingTo("100");
        assertThat(points.get(0).expense()).isEqualByComparingTo("60");
    }

    // ---------- monthly balance (net worth) ----------

    @Test
    void monthlyBalance_netWorthAtEachMonthEnd() {
        when(accounts.all()).thenReturn(List.of(ahorro)); // initial 0
        when(movements.netByAccountUntil(2L, LocalDate.of(2024, 2, 29))).thenReturn(eur("100"));
        when(movements.netByAccountUntil(2L, LocalDate.of(2024, 3, 31))).thenReturn(eur("300"));

        List<BalancePointView> points = service.monthlyBalance(2, YearMonth.of(2024, 3), 2L);

        assertThat(points).extracting(BalancePointView::month).containsExactly("2024-02", "2024-03");
        assertThat(points.get(0).balance()).isEqualByComparingTo("100");
        assertThat(points.get(1).balance()).isEqualByComparingTo("300");
    }

    // ---------- account comparison ----------

    @Test
    void accountComparison_buildsLabelsAndPerAccountSeries() {
        when(accounts.all()).thenReturn(List.of(corriente));
        when(movements.sumByType(eq(TransactionType.INCOME), any(), any(), eq(1L))).thenReturn(eur("500"));
        when(movements.sumByType(eq(TransactionType.EXPENSE), any(), any(), eq(1L))).thenReturn(eur("200"));

        AccountComparisonView comparison = service.accountComparison(2, YearMonth.of(2024, 2));

        assertThat(comparison.months()).containsExactly("2024-01", "2024-02");
        assertThat(comparison.accounts()).singleElement().satisfies(serie -> {
            assertThat(serie.accountId()).isEqualTo(1L);
            assertThat(serie.income()).containsExactly(eur("500"), eur("500"));
            assertThat(serie.expense()).containsExactly(eur("200"), eur("200"));
        });
    }

    // ---------- budget status ----------

    @Test
    void budgetStatus_computesSpentRemainingAndSortsBySpentDesc() {
        when(budgets.forMonth(2024, 3, 1L)).thenReturn(List.of(
                new ReportBudget(500L, 1L, "Corriente", 10L, "Comida", "#a", eur("200")),
                new ReportBudget(501L, 1L, "Corriente", 11L, "Ocio", "#b", eur("300"))));
        LocalDate from = LocalDate.of(2024, 3, 1), to = LocalDate.of(2024, 3, 31);
        when(movements.spentByCategoryTree(10L, from, to, 1L)).thenReturn(eur("150"));
        when(movements.spentByCategoryTree(11L, from, to, 1L)).thenReturn(eur("250"));

        List<BudgetStatusView> result = service.budgetStatus(2024, 3, 1L);

        // Sorted by spent desc: Ocio (250) before Comida (150).
        assertThat(result).extracting(BudgetStatusView::category).containsExactly("Ocio", "Comida");
        assertThat(result.get(0).spent()).isEqualByComparingTo("250");
        assertThat(result.get(0).remaining()).isEqualByComparingTo("50"); // 300 - 250
        assertThat(result.get(1).remaining()).isEqualByComparingTo("50"); // 200 - 150
    }

    @Test
    void budgetStatus_aggregateScopeQueriesAllAccounts() {
        when(budgets.forMonth(2024, 3, null)).thenReturn(List.of(
                new ReportBudget(500L, 1L, "Corriente", 10L, "Comida", "#a", eur("200"))));
        when(movements.spentByCategoryTree(eq(10L), any(), any(), eq(1L))).thenReturn(eur("80"));

        List<BudgetStatusView> result = service.budgetStatus(2024, 3, null);

        assertThat(result).singleElement().satisfies(b -> {
            assertThat(b.spent()).isEqualByComparingTo("80");
            assertThat(b.remaining()).isEqualByComparingTo("120");
        });
    }

    @Test
    void findMovements_delegatesToTheQueryPort() {
        LocalDate from = LocalDate.of(2024, 3, 1), to = LocalDate.of(2024, 3, 31);
        Page<MovementView> expected = new Page<>(List.of(), 1, 10, 42);
        when(movementQuery.search(from, to, 1L, 2L, 1, 10)).thenReturn(expected);

        Page<MovementView> result = service.findMovements(from, to, 1L, 2L, 1, 10);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void findMovements_rejectsNegativePageOrNonPositiveSize() {
        LocalDate from = LocalDate.of(2024, 3, 1), to = LocalDate.of(2024, 3, 31);

        assertThatThrownBy(() -> service.findMovements(from, to, null, null, -1, 10))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.findMovements(from, to, null, null, 0, 0))
                .isInstanceOf(ValidationException.class);
    }
}
