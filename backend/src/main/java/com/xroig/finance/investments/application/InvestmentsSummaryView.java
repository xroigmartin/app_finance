package com.xroig.finance.investments.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Read model (CQRS) of the global multi-portfolio summary (RF-10, §6): the
 * aggregate wealth of every portfolio <b>in EUR</b> (the home app's currency —
 * each portfolio converts from its base via the EUR pivot when it differs), with
 * the per-portfolio breakdown for the dashboard card. {@code valuationDate} is
 * the oldest of the dates used (null when nothing is quoted).
 */
public record InvestmentsSummaryView(BigDecimal totalValue,
                                     LocalDate valuationDate,
                                     List<PortfolioValueView> portfolios) {

    /** One portfolio's value converted to EUR. */
    public record PortfolioValueView(long portfolioId,
                                     String name,
                                     String baseCurrency,
                                     BigDecimal value,
                                     LocalDate valuationDate) {
    }
}
