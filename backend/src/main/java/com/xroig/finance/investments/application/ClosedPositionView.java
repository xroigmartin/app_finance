package com.xroig.finance.investments.application;

import java.math.BigDecimal;

/**
 * Read model (CQRS) of the portfolio's realized P&L (RF-nuevo): one row per
 * instrument and calendar year of sale with a non-zero realized amount, in the
 * portfolio's base currency, average capitalized cost (RN-3). Includes partial
 * sales of instruments whose position is still open today — the frontend
 * aggregates the TOTAL row and applies the year selector, same pattern as
 * {@link IncomeView}.
 */
public record ClosedPositionView(long securityId, String isin, String name, String ticker,
                                 String currency, int year, BigDecimal realizedPnl) {
}
