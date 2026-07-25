package com.xroig.finance.investments.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read model (CQRS) of the portfolio's income (RF-7): dividends/interest per
 * instrument and month — gross, the {@code TAX} withholding linked to the
 * instrument (§9) and the net — plus the fees and withholdings paid per month,
 * every figure in the portfolio's base currency (fixed amounts converted with
 * their own snapshot, RN-7a). Months serialize as {@code "YYYY-MM"}; entries are
 * ordered by month and instrument name. {@code securityId}/{@code name} are null
 * for income without instrument (broker interest).
 */
public record IncomeView(long portfolioId,
                         String baseCurrency,
                         List<IncomeEntryView> incomes,
                         List<MonthAmountView> fees,
                         List<MonthAmountView> taxes) {

    /** Income of one instrument in one month. */
    public record IncomeEntryView(Long securityId, String name, String month,
                                  BigDecimal gross, BigDecimal withheld, BigDecimal net) {
    }

    /** One month's aggregate (fees or withholdings paid), positive magnitude. */
    public record MonthAmountView(String month, BigDecimal amount) {
    }
}
