package com.xroig.finance.reporting.infrastructure.web;

import com.xroig.finance.reporting.application.AccountComparisonView;
import com.xroig.finance.reporting.application.CategoryAmountView;
import com.xroig.finance.reporting.application.SummaryView;
import com.xroig.finance.reporting.application.port.DashboardReports;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the read-only {@link DashboardController} with a mocked {@link
 * DashboardReports}: each endpoint delegates, resolving the default year/month to "now",
 * clamping {@code months} to [1, 36] and building the end month.
 */
@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock private DashboardReports reports;
    @InjectMocks private DashboardController controller;

    @Test
    void summary_passesExplicitYearMonthAndAccount() {
        SummaryView expected = mock(SummaryView.class);
        when(reports.summary(2024, 3, 1L)).thenReturn(expected);

        assertThat(controller.summary(2024, 3, 1L)).isSameAs(expected);
    }

    @Test
    void summary_defaultsToCurrentYearAndMonth() {
        LocalDate now = LocalDate.now();
        controller.summary(null, null, null);
        verify(reports).summary(now.getYear(), now.getMonthValue(), null);
    }

    @Test
    void expensesByCategory_delegatesWithExplicitPeriod() {
        List<CategoryAmountView> expected = List.of();
        when(reports.expensesByCategory(2024, 3, 1L)).thenReturn(expected);

        assertThat(controller.expensesByCategory(2024, 3, 1L)).isSameAs(expected);
    }

    @Test
    void incomeByCategory_defaultsToCurrentPeriod() {
        LocalDate now = LocalDate.now();
        controller.incomeByCategory(null, null, null);
        verify(reports).incomeByCategory(now.getYear(), now.getMonthValue(), null);
    }

    @Test
    void budgets_defaultsToCurrentPeriod() {
        LocalDate now = LocalDate.now();
        controller.budgets(null, null, 2L);
        verify(reports).budgetStatus(now.getYear(), now.getMonthValue(), 2L);
    }

    @Test
    void monthly_clampsMonthsUpperBoundAndBuildsEndMonth() {
        controller.monthly(100, 2024, 3, 1L);
        verify(reports).monthlyEvolution(36, YearMonth.of(2024, 3), 1L);
    }

    @Test
    void monthly_clampsMonthsLowerBound() {
        controller.monthly(0, 2024, 3, null);
        verify(reports).monthlyEvolution(1, YearMonth.of(2024, 3), null);
    }

    @Test
    void monthlyBalance_delegatesWithExplicitMonths() {
        controller.monthlyBalance(6, 2024, 3, 1L);
        verify(reports).monthlyBalance(6, YearMonth.of(2024, 3), 1L);
    }

    @Test
    void byAccount_defaultsEndMonthToCurrent() {
        AccountComparisonView expected = new AccountComparisonView(List.of(), List.of());
        when(reports.accountComparison(12, YearMonth.now())).thenReturn(expected);

        assertThat(controller.byAccount(12, null, null)).isSameAs(expected);
    }
}
