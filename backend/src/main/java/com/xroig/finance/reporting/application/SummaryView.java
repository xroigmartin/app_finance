package com.xroig.finance.reporting.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read model (CQRS) for the dashboard summary. {@code totalBalance} is the balance at the
 * end of the selected month (of the selected account, or of all accounts); year figures
 * cover the whole calendar year of the selected month.
 *
 * <p>Two profitability views, each as absolute delta plus percentage over the balance at
 * the start of the period (end of previous month/year): balance growth (end minus start,
 * includes transfers) and savings yield (income minus expense, excludes transfers).
 * Percentages are {@code null} when the starting balance is not positive. Faithful to the
 * legacy JSON for every field the UI consumes.
 */
public record SummaryView(BigDecimal totalBalance,
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
                          List<AccountBalanceView> accounts) {

    /** The balance of one account at the end of the selected month, as nested in the summary. */
    public record AccountBalanceView(Long id, String name, String type, BigDecimal balance) {
    }
}
