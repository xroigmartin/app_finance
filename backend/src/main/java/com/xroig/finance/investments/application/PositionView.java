package com.xroig.finance.investments.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read model (CQRS) of one valued position (RF-5): quantity, capitalized cost and
 * latent P&L in the portfolio's base currency, the market price in the security's
 * quote currency at the latest quote ≤ the valuation date (RN-6), and the weight
 * over the portfolio's total value (positions + cash). When no quote exists the
 * position is valued at cost with {@code pricedAtCost} raised (RN-6 notice);
 * {@code averageCost} and {@code latentPnlPercent} are null when the quantity or
 * the cost are not positive (closed or negative position, RN-4); {@code weight}
 * is null when the portfolio's total value is zero.
 */
public record PositionView(long securityId,
                           String isin,
                           String name,
                           String ticker,
                           String currency,
                           BigDecimal quantity,
                           BigDecimal averageCost,
                           BigDecimal costBasis,
                           BigDecimal marketPrice,
                           LocalDate quoteDate,
                           BigDecimal marketValue,
                           BigDecimal latentPnl,
                           BigDecimal latentPnlPercent,
                           BigDecimal weight,
                           boolean pricedAtCost) {
}
