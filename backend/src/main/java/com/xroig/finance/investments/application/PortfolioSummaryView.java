package com.xroig.finance.investments.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Read model (CQRS) of the portfolio's header KPIs (§6): every monetary figure in
 * the portfolio's base currency except {@code cashByCurrency} (RF-6, one entry per
 * currency in its own currency). Net contributions and the year's gross dividends
 * are fixed amounts converted with their own snapshot (RN-7a); the total value and
 * the latent P&L are valued at the latest quote/rate ≤ today (RN-6/RN-7b).
 * {@code valuationDate} is the oldest quote date used (null when nothing is
 * quoted); {@code latentPnlPercent} is over the capitalized cost (RN-3).
 */
public record PortfolioSummaryView(long portfolioId,
                                   String name,
                                   String baseCurrency,
                                   BigDecimal totalValue,
                                   LocalDate valuationDate,
                                   BigDecimal netContributions,
                                   BigDecimal latentPnl,
                                   BigDecimal latentPnlPercent,
                                   Map<String, BigDecimal> cashByCurrency,
                                   BigDecimal dividendsThisYear) {
}
