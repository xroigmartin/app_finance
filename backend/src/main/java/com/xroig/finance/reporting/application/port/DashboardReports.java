package com.xroig.finance.reporting.application.port;

import com.xroig.finance.reporting.application.AccountComparisonView;
import com.xroig.finance.reporting.application.BalancePointView;
import com.xroig.finance.reporting.application.BudgetStatusView;
import com.xroig.finance.reporting.application.CategoryAmountView;
import com.xroig.finance.reporting.application.MonthlyPointView;
import com.xroig.finance.reporting.application.SummaryView;

import java.time.YearMonth;
import java.util.List;

/**
 * Inbound port (CQRS read facade): the dashboard queries the web adapter delegates to, once
 * it has resolved the default year/month and clamped {@code months}. Implemented by {@code
 * ReportingService}.
 */
public interface DashboardReports {

    SummaryView summary(int year, int month, Long accountId);

    List<CategoryAmountView> expensesByCategory(int year, int month, Long accountId);

    List<CategoryAmountView> incomeByCategory(int year, int month, Long accountId);

    List<BudgetStatusView> budgetStatus(int year, int month, Long accountId);

    List<MonthlyPointView> monthlyEvolution(int months, YearMonth end, Long accountId);

    List<BalancePointView> monthlyBalance(int months, YearMonth end, Long accountId);

    AccountComparisonView accountComparison(int months, YearMonth end);
}
