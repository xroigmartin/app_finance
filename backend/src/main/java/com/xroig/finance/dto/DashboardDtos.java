package com.xroig.finance.dto;

import java.math.BigDecimal;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    /**
     * {@code totalBalance} is the balance at the end of the selected month
     * (of the selected account, or of all accounts). Year figures cover the
     * whole calendar year of the selected month.
     * <p>
     * Two profitability views, each as absolute delta plus percentage over
     * the balance at the start of the period (end of previous month / year):
     * <ul>
     *   <li>balance growth: end balance minus start balance — includes
     *       transfers, measures how much the account grew;</li>
     *   <li>savings yield: income minus expense — excludes transfers,
     *       measures what the account's own activity produced.</li>
     * </ul>
     * Percentages are {@code null} when the starting balance is not positive.
     */
    public record Summary(BigDecimal totalBalance,
                          BigDecimal monthIncome,
                          BigDecimal monthExpense,
                          BigDecimal monthSavings,
                          BigDecimal yearIncome,
                          BigDecimal yearExpense,
                          BigDecimal yearSavings,
                          BigDecimal monthBalanceDelta,
                          BigDecimal yearBalanceDelta,
                          BigDecimal monthGrowthPct,
                          BigDecimal yearGrowthPct,
                          BigDecimal monthSavingsYieldPct,
                          BigDecimal yearSavingsYieldPct,
                          List<AccountBalance> accounts) {
    }

    public record AccountBalance(Long id, String name, String type, BigDecimal balance) {
    }

    public record CategoryAmount(String category, String color, BigDecimal amount) {
    }

    public record MonthlyPoint(String month, BigDecimal income, BigDecimal expense) {
    }

    /** Balance (net worth) at the end of a given month. */
    public record BalancePoint(String month, BigDecimal balance) {
    }

    /** Monthly income/expense series for a single account, for comparisons. */
    public record AccountSeries(Long accountId,
                                String name,
                                List<BigDecimal> income,
                                List<BigDecimal> expense) {
    }

    public record AccountComparison(List<String> months, List<AccountSeries> accounts) {
    }

    public record BudgetStatus(Long budgetId,
                               Long accountId,
                               String account,
                               Long categoryId,
                               String category,
                               String color,
                               BigDecimal budget,
                               BigDecimal spent,
                               BigDecimal remaining) {
    }
}
