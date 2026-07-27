package com.xroig.finance.investments.domain;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Result of {@link IncomeCalculator} (RF-7), everything in the portfolio's base
 * currency: dividends/interest per instrument and month (gross, withheld, net)
 * plus the fees and income withholdings paid per month as positive magnitudes.
 * {@code TRADE_TAX} rows never appear here — they are acquisition cost (RN-3/§9).
 */
public record IncomeStatement(List<InstrumentIncome> incomes,
                              Map<YearMonth, CurrencyMoney> feesByMonth,
                              Map<YearMonth, CurrencyMoney> taxesByMonth) {
}
