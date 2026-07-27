package com.xroig.finance.investments.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Read model (CQRS) of the portfolio's performance (RN-8): the cumulative TWR of
 * the whole observed period and the annualized XIRR, as percentages (scale 2),
 * for the total and per open position — null when not computable (no history,
 * no quotes, no root). Per position, the XIRR uses the instrument's real
 * cashflows (trades, income, costs) plus its current value, and the TWR chains
 * its quote-date valuations; with quotes only at import dates both are
 * approximations over those points (§9).
 */
public record PerformanceView(long portfolioId,
                              String baseCurrency,
                              LocalDate valuationDate,
                              BigDecimal twrPercent,
                              BigDecimal xirrPercent,
                              List<PositionPerformanceView> positions) {

    /** One open position's performance. */
    public record PositionPerformanceView(long securityId, String name,
                                          BigDecimal twrPercent, BigDecimal xirrPercent) {
    }
}
