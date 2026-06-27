package com.xroig.finance.reporting.infrastructure.web;

import com.xroig.finance.reporting.application.CategoryAmountView;
import com.xroig.finance.reporting.application.port.DashboardReports;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static com.xroig.finance.Fixtures.eur;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HTTP-contract test ({@code @WebMvcTest}) for the read-only {@link DashboardController}.
 * The aggregation maths is in {@code ReportingServiceTest}; here we pin what the slice
 * adds: query-parameter binding with the current-date fallback, the {@code months} clamp
 * (1..36) wired to the {@code @RequestParam}, list/DTO JSON serialization, and a malformed
 * numeric param → 400 — verifying delegation to a mocked {@link DashboardReports}.
 */
@WebMvcTest(DashboardController.class)
class DashboardControllerMvcTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private DashboardReports reports;

    // ---------- current-date fallback ----------

    @Test
    void summary_bindsParams() {
        assertThat(mvc.get().uri("/api/dashboard/summary?year=2024&month=3&accountId=1")).hasStatusOk();
        verify(reports).summary(2024, 3, 1L);
    }

    @Test
    void summary_withoutParams_usesCurrentYearMonthAndAllAccounts() {
        LocalDate now = LocalDate.now();
        assertThat(mvc.get().uri("/api/dashboard/summary")).hasStatusOk();
        verify(reports).summary(now.getYear(), now.getMonthValue(), null);
    }

    @Test
    void summary_malformedYear_returns400() {
        assertThat(mvc.get().uri("/api/dashboard/summary?year=abc")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    // ---------- list endpoints: JSON serialization + routing ----------

    @Test
    void expensesByCategory_returnsJsonList() {
        when(reports.expensesByCategory(2024, 3, 1L))
                .thenReturn(List.of(new CategoryAmountView("Comida", "#abc", eur("50"))));

        assertThat(mvc.get().uri("/api/dashboard/expenses-by-category?year=2024&month=3&accountId=1"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[0].category").isEqualTo("Comida");
    }

    @Test
    void incomeByCategory_routesAndDelegates() {
        when(reports.incomeByCategory(2024, 3, null)).thenReturn(List.of());
        assertThat(mvc.get().uri("/api/dashboard/income-by-category?year=2024&month=3")).hasStatusOk();
        verify(reports).incomeByCategory(2024, 3, null);
    }

    @Test
    void budgets_routesAndDelegates() {
        when(reports.budgetStatus(2024, 3, 1L)).thenReturn(List.of());
        assertThat(mvc.get().uri("/api/dashboard/budgets?year=2024&month=3&accountId=1")).hasStatusOk();
        verify(reports).budgetStatus(2024, 3, 1L);
    }

    // ---------- months clamp (1..36) wired to the query param ----------

    @Test
    void monthly_clampsMonthsAbove36() {
        assertThat(mvc.get().uri("/api/dashboard/monthly?months=100")).hasStatusOk();
        verify(reports).monthlyEvolution(eq(36), any(YearMonth.class), isNull());
    }

    @Test
    void monthly_clampsMonthsBelow1() {
        assertThat(mvc.get().uri("/api/dashboard/monthly?months=0")).hasStatusOk();
        verify(reports).monthlyEvolution(eq(1), any(YearMonth.class), isNull());
    }

    @Test
    void monthlyBalance_clampsAndDelegates() {
        assertThat(mvc.get().uri("/api/dashboard/monthly-balance?months=50&accountId=1")).hasStatusOk();
        verify(reports).monthlyBalance(eq(36), any(YearMonth.class), eq(1L));
    }

    @Test
    void byAccount_passesEndMonthBuiltFromYearAndMonth() {
        assertThat(mvc.get().uri("/api/dashboard/by-account?months=6&year=2024&month=3")).hasStatusOk();
        verify(reports).accountComparison(6, YearMonth.of(2024, 3));
    }
}
