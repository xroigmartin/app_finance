package com.xroig.finance.service;

import com.xroig.finance.dto.DashboardDtos.AccountComparison;
import com.xroig.finance.dto.DashboardDtos.BalancePoint;
import com.xroig.finance.dto.DashboardDtos.BudgetStatus;
import com.xroig.finance.dto.DashboardDtos.CategoryAmount;
import com.xroig.finance.dto.DashboardDtos.MonthlyPoint;
import com.xroig.finance.dto.DashboardDtos.Summary;
import com.xroig.finance.model.Account;
import com.xroig.finance.model.Category;
import com.xroig.finance.model.TransactionType;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.BudgetRepository;
import com.xroig.finance.repository.TransactionRepository;
import com.xroig.finance.repository.TransferRepository;
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

import static com.xroig.finance.Fixtures.account;
import static com.xroig.finance.Fixtures.budget;
import static com.xroig.finance.Fixtures.category;
import static com.xroig.finance.Fixtures.eur;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Service tests for {@link DashboardService} with mocked repositories
 * (stage E2d): summary (balances, savings, deltas, yield %), byCategory,
 * monthly series, account comparison and budget status.
 *
 * <p>Lenient strictness because {@code summary} fans out across many repository
 * calls; each test stubs the figures it asserts on.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private TransferRepository transferRepository;
    @Mock private BudgetRepository budgetRepository;
    @InjectMocks private DashboardService service;

    private final Account corriente = account(1, "Corriente", eur("1000"));
    private final Account ahorro = account(2, "Ahorro", eur("0"));

    private static List<Object[]> rows(Object[]... rows) {
        return List.of(rows);
    }

    @BeforeEach
    void noTransfersByDefault() {
        when(transferRepository.totalInUntil(any(), any())).thenReturn(BigDecimal.ZERO);
        when(transferRepository.totalOutUntil(any(), any())).thenReturn(BigDecimal.ZERO);
    }

    // ---------- summary ----------

    @Test
    void summary_computesBalancesSavingsDeltasAndYieldPercents() {
        when(accountRepository.findAll()).thenReturn(List.of(corriente));
        // balanceUntil = 1000 (initial) + net + transfers(0). Net per relevant date:
        when(transactionRepository.netTotalByAccountUntil(1L, LocalDate.of(2024, 3, 31))).thenReturn(eur("500"));
        when(transactionRepository.netTotalByAccountUntil(1L, LocalDate.of(2024, 2, 29))).thenReturn(eur("200"));
        when(transactionRepository.netTotalByAccountUntil(1L, LocalDate.of(2023, 12, 31))).thenReturn(eur("0"));
        when(transactionRepository.netTotalByAccountUntil(1L, LocalDate.of(2024, 12, 31))).thenReturn(eur("800"));
        LocalDate mFrom = LocalDate.of(2024, 3, 1), mTo = LocalDate.of(2024, 3, 31);
        LocalDate yFrom = LocalDate.of(2024, 1, 1), yTo = LocalDate.of(2024, 12, 31);
        when(transactionRepository.sumByTypeAndPeriod(TransactionType.INCOME, mFrom, mTo, 1L)).thenReturn(eur("400"));
        when(transactionRepository.sumByTypeAndPeriod(TransactionType.EXPENSE, mFrom, mTo, 1L)).thenReturn(eur("100"));
        when(transactionRepository.sumByTypeAndPeriod(TransactionType.INCOME, yFrom, yTo, 1L)).thenReturn(eur("5000"));
        when(transactionRepository.sumByTypeAndPeriod(TransactionType.EXPENSE, yFrom, yTo, 1L)).thenReturn(eur("3000"));

        Summary s = service.summary(2024, 3, 1L);

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
        when(accountRepository.findAll()).thenReturn(List.of(ahorro)); // initial 0
        when(transactionRepository.netTotalByAccountUntil(eq(2L), any())).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumByTypeAndPeriod(any(), any(), any(), any())).thenReturn(eur("100"));

        Summary s = service.summary(2024, 3, 2L);

        assertThat(s.monthGrowthPct()).isNull();
        assertThat(s.yearGrowthPct()).isNull();
        assertThat(s.monthSavingsYieldPct()).isNull();
        assertThat(s.yearSavingsYieldPct()).isNull();
    }

    @Test
    void summary_totalBalanceFiltersBySelectedAccount() {
        when(accountRepository.findAll()).thenReturn(List.of(corriente, ahorro));
        when(transactionRepository.netTotalByAccountUntil(eq(1L), any())).thenReturn(eur("500"));
        when(transactionRepository.netTotalByAccountUntil(eq(2L), any())).thenReturn(eur("700"));
        when(transactionRepository.sumByTypeAndPeriod(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        Summary s = service.summary(2024, 3, 2L);

        // Only account 2 (initial 0 + net 700) counts towards the total, but
        // both accounts are still listed.
        assertThat(s.totalBalance()).isEqualByComparingTo("700");
        assertThat(s.accounts()).extracting(a -> a.id()).containsExactly(1L, 2L);
    }

    // ---------- byCategory ----------

    @Test
    void expensesByCategory_mapsRowsToCategoryAmounts() {
        when(transactionRepository.sumByCategory(eq(TransactionType.EXPENSE),
                eq(LocalDate.of(2024, 3, 1)), eq(LocalDate.of(2024, 3, 31)), eq(1L)))
                .thenReturn(rows(
                        new Object[] {"Comida", "#ff0000", eur("120")},
                        new Object[] {"Ocio", "#00ff00", eur("40")}));

        List<CategoryAmount> result = service.expensesByCategory(2024, 3, 1L);

        assertThat(result).extracting(CategoryAmount::category).containsExactly("Comida", "Ocio");
        assertThat(result.get(0).color()).isEqualTo("#ff0000");
        assertThat(result.get(0).amount()).isEqualByComparingTo("120");
    }

    @Test
    void incomeByCategory_queriesIncomeType() {
        when(transactionRepository.sumByCategory(eq(TransactionType.INCOME), any(), any(), any()))
                .thenReturn(rows(new Object[] {"Nómina", "#0000ff", eur("2000")}));

        List<CategoryAmount> result = service.incomeByCategory(2024, 3, null);

        assertThat(result).singleElement().satisfies(c -> {
            assertThat(c.category()).isEqualTo("Nómina");
            assertThat(c.amount()).isEqualByComparingTo("2000");
        });
    }

    // ---------- monthly evolution ----------

    @Test
    void monthlyEvolution_buildsPointsFromOldestToNewest() {
        when(transactionRepository.sumByTypeAndPeriod(eq(TransactionType.INCOME), any(), any(), eq(1L)))
                .thenReturn(eur("100"));
        when(transactionRepository.sumByTypeAndPeriod(eq(TransactionType.EXPENSE), any(), any(), eq(1L)))
                .thenReturn(eur("60"));

        List<MonthlyPoint> points = service.monthlyEvolution(3, YearMonth.of(2024, 3), 1L);

        assertThat(points).extracting(MonthlyPoint::month)
                .containsExactly("2024-01", "2024-02", "2024-03");
        assertThat(points.get(0).income()).isEqualByComparingTo("100");
        assertThat(points.get(0).expense()).isEqualByComparingTo("60");
    }

    // ---------- monthly balance (net worth) ----------

    @Test
    void monthlyBalance_netWorthAtEachMonthEnd() {
        when(accountRepository.findAll()).thenReturn(List.of(ahorro)); // initial 0
        when(transactionRepository.netTotalByAccountUntil(2L, LocalDate.of(2024, 2, 29))).thenReturn(eur("100"));
        when(transactionRepository.netTotalByAccountUntil(2L, LocalDate.of(2024, 3, 31))).thenReturn(eur("300"));

        List<BalancePoint> points = service.monthlyBalance(2, YearMonth.of(2024, 3), 2L);

        assertThat(points).extracting(BalancePoint::month).containsExactly("2024-02", "2024-03");
        assertThat(points.get(0).balance()).isEqualByComparingTo("100");
        assertThat(points.get(1).balance()).isEqualByComparingTo("300");
    }

    // ---------- account comparison ----------

    @Test
    void accountComparison_buildsLabelsAndPerAccountSeries() {
        when(accountRepository.findAll()).thenReturn(List.of(corriente));
        when(transactionRepository.sumByTypeAndPeriod(eq(TransactionType.INCOME), any(), any(), eq(1L)))
                .thenReturn(eur("500"));
        when(transactionRepository.sumByTypeAndPeriod(eq(TransactionType.EXPENSE), any(), any(), eq(1L)))
                .thenReturn(eur("200"));

        AccountComparison comparison = service.accountComparison(2, YearMonth.of(2024, 2));

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
        Category comida = category(10, "Comida", TransactionType.EXPENSE, corriente);
        Category ocio = category(11, "Ocio", TransactionType.EXPENSE, corriente);
        when(budgetRepository.findByAccountIdAndYearAndMonth(1L, 2024, 3)).thenReturn(List.of(
                budget(500L, corriente, comida, 2024, 3, eur("200")),
                budget(501L, corriente, ocio, 2024, 3, eur("300"))));
        LocalDate from = LocalDate.of(2024, 3, 1), to = LocalDate.of(2024, 3, 31);
        when(transactionRepository.sumByCategoryTreeAndPeriod(10L, from, to, 1L)).thenReturn(eur("150"));
        when(transactionRepository.sumByCategoryTreeAndPeriod(11L, from, to, 1L)).thenReturn(eur("250"));

        List<BudgetStatus> result = service.budgetStatus(2024, 3, 1L);

        // Sorted by spent desc: Ocio (250) before Comida (150).
        assertThat(result).extracting(BudgetStatus::category).containsExactly("Ocio", "Comida");
        assertThat(result.get(0).spent()).isEqualByComparingTo("250");
        assertThat(result.get(0).remaining()).isEqualByComparingTo("50"); // 300 - 250
        assertThat(result.get(1).remaining()).isEqualByComparingTo("50"); // 200 - 150
    }

    @Test
    void budgetStatus_aggregateScopeUsesFindByYearAndMonth() {
        Category comida = category(10, "Comida", TransactionType.EXPENSE, corriente);
        when(budgetRepository.findByYearAndMonth(2024, 3)).thenReturn(List.of(
                budget(500L, corriente, comida, 2024, 3, eur("200"))));
        when(transactionRepository.sumByCategoryTreeAndPeriod(eq(10L), any(), any(), eq(1L)))
                .thenReturn(eur("80"));

        List<BudgetStatus> result = service.budgetStatus(2024, 3, null);

        assertThat(result).singleElement().satisfies(b -> {
            assertThat(b.spent()).isEqualByComparingTo("80");
            assertThat(b.remaining()).isEqualByComparingTo("120");
        });
    }
}
