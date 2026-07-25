package com.xroig.finance.reporting.infrastructure.web;

import com.xroig.finance.reporting.application.AccountComparisonView;
import com.xroig.finance.reporting.application.BalancePointView;
import com.xroig.finance.reporting.application.BudgetStatusView;
import com.xroig.finance.reporting.application.CategoryAmountView;
import com.xroig.finance.reporting.application.MonthlyPointView;
import com.xroig.finance.reporting.application.SummaryView;
import com.xroig.finance.reporting.application.port.DashboardReports;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Inbound web adapter for the reporting context (read-only). Thin: it resolves the default
 * year/month to "now", clamps {@code months} to [1, 36], builds the end month, and delegates
 * to the {@link DashboardReports} inbound port, returning the read models.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardReports reports;

    public DashboardController(DashboardReports reports) {
        this.reports = reports;
    }

    @GetMapping("/summary")
    public SummaryView summary(@RequestParam(required = false) Integer year,
                               @RequestParam(required = false) Integer month,
                               @RequestParam(required = false) Long accountId) {
        LocalDate now = LocalDate.now();
        return reports.summary(
                year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue(),
                accountId);
    }

    @GetMapping("/expenses-by-category")
    public List<CategoryAmountView> expensesByCategory(@RequestParam(required = false) Integer year,
                                                       @RequestParam(required = false) Integer month,
                                                       @RequestParam(required = false) Long accountId) {
        LocalDate now = LocalDate.now();
        return reports.expensesByCategory(
                year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue(),
                accountId);
    }

    @GetMapping("/budgets")
    public List<BudgetStatusView> budgets(@RequestParam(required = false) Integer year,
                                          @RequestParam(required = false) Integer month,
                                          @RequestParam(required = false) Long accountId) {
        LocalDate now = LocalDate.now();
        return reports.budgetStatus(
                year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue(),
                accountId);
    }

    @GetMapping("/monthly")
    public List<MonthlyPointView> monthly(@RequestParam(defaultValue = "12") int months,
                                          @RequestParam(required = false) Integer year,
                                          @RequestParam(required = false) Integer month,
                                          @RequestParam(required = false) Long accountId) {
        return reports.monthlyEvolution(clampMonths(months), endMonth(year, month), accountId);
    }

    @GetMapping("/income-by-category")
    public List<CategoryAmountView> incomeByCategory(@RequestParam(required = false) Integer year,
                                                     @RequestParam(required = false) Integer month,
                                                     @RequestParam(required = false) Long accountId) {
        LocalDate now = LocalDate.now();
        return reports.incomeByCategory(
                year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue(),
                accountId);
    }

    @GetMapping("/monthly-balance")
    public List<BalancePointView> monthlyBalance(@RequestParam(defaultValue = "12") int months,
                                                 @RequestParam(required = false) Integer year,
                                                 @RequestParam(required = false) Integer month,
                                                 @RequestParam(required = false) Long accountId) {
        return reports.monthlyBalance(clampMonths(months), endMonth(year, month), accountId);
    }

    @GetMapping("/by-account")
    public AccountComparisonView byAccount(@RequestParam(defaultValue = "12") int months,
                                           @RequestParam(required = false) Integer year,
                                           @RequestParam(required = false) Integer month) {
        return reports.accountComparison(clampMonths(months), endMonth(year, month));
    }

    private static int clampMonths(int months) {
        return Math.min(Math.max(months, 1), 36);
    }

    private static YearMonth endMonth(Integer year, Integer month) {
        LocalDate now = LocalDate.now();
        return YearMonth.of(year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue());
    }
}
