package com.xroig.finance.controller;

import com.xroig.finance.dto.DashboardDtos.AccountComparison;
import com.xroig.finance.dto.DashboardDtos.BalancePoint;
import com.xroig.finance.dto.DashboardDtos.BudgetStatus;
import com.xroig.finance.dto.DashboardDtos.CategoryAmount;
import com.xroig.finance.dto.DashboardDtos.MonthlyPoint;
import com.xroig.finance.dto.DashboardDtos.Summary;
import com.xroig.finance.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public Summary summary(@RequestParam(required = false) Integer year,
                           @RequestParam(required = false) Integer month,
                           @RequestParam(required = false) Long accountId) {
        LocalDate now = LocalDate.now();
        return dashboardService.summary(
                year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue(),
                accountId);
    }

    @GetMapping("/expenses-by-category")
    public List<CategoryAmount> expensesByCategory(@RequestParam(required = false) Integer year,
                                                   @RequestParam(required = false) Integer month,
                                                   @RequestParam(required = false) Long accountId) {
        LocalDate now = LocalDate.now();
        return dashboardService.expensesByCategory(
                year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue(),
                accountId);
    }

    @GetMapping("/budgets")
    public List<BudgetStatus> budgets(@RequestParam(required = false) Integer year,
                                      @RequestParam(required = false) Integer month,
                                      @RequestParam(required = false) Long accountId) {
        LocalDate now = LocalDate.now();
        return dashboardService.budgetStatus(
                year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue(),
                accountId);
    }

    @GetMapping("/monthly")
    public List<MonthlyPoint> monthly(@RequestParam(defaultValue = "12") int months,
                                      @RequestParam(required = false) Integer year,
                                      @RequestParam(required = false) Integer month,
                                      @RequestParam(required = false) Long accountId) {
        return dashboardService.monthlyEvolution(clampMonths(months), endMonth(year, month), accountId);
    }

    @GetMapping("/income-by-category")
    public List<CategoryAmount> incomeByCategory(@RequestParam(required = false) Integer year,
                                                 @RequestParam(required = false) Integer month,
                                                 @RequestParam(required = false) Long accountId) {
        LocalDate now = LocalDate.now();
        return dashboardService.incomeByCategory(
                year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue(),
                accountId);
    }

    @GetMapping("/monthly-balance")
    public List<BalancePoint> monthlyBalance(@RequestParam(defaultValue = "12") int months,
                                             @RequestParam(required = false) Integer year,
                                             @RequestParam(required = false) Integer month,
                                             @RequestParam(required = false) Long accountId) {
        return dashboardService.monthlyBalance(clampMonths(months), endMonth(year, month), accountId);
    }

    @GetMapping("/by-account")
    public AccountComparison byAccount(@RequestParam(defaultValue = "12") int months,
                                       @RequestParam(required = false) Integer year,
                                       @RequestParam(required = false) Integer month) {
        return dashboardService.accountComparison(clampMonths(months), endMonth(year, month));
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
