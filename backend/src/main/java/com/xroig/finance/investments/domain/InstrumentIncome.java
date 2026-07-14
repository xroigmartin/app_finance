package com.xroig.finance.investments.domain;

import java.time.YearMonth;

/**
 * Income of one instrument in one month (RF-7), in the portfolio's base currency:
 * gross dividends/interest collected, the {@code TAX} withholding linked to the
 * instrument (§9, positive magnitude) and the resulting net. {@code securityId}
 * is null for income without instrument (broker interest).
 */
public record InstrumentIncome(SecurityId securityId, YearMonth month,
                               CurrencyMoney gross, CurrencyMoney withheld, CurrencyMoney net) {
}
