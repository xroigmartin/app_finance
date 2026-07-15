package com.xroig.finance.investments.application;

import java.util.List;

/**
 * Read-side (CQRS) outbound port: resolves the investment read models from
 * persistence without reconstructing aggregates to read. Valuation follows RN-6
 * (latest quote ≤ today, at cost with a notice without one) and RN-7 (snapshot
 * conversion for fixed amounts, rate table for valuation at a date).
 */
public interface InvestmentQueryPort {

    /** Current valued positions of the portfolio (RF-5). */
    List<PositionView> positions(long portfolioId);

    /** Header KPIs of the portfolio (§6). */
    PortfolioSummaryView summary(long portfolioId);

    /** Value vs cumulative contributions series for the evolution chart (§6/§7). */
    List<ValuationHistoryView> valuationHistory(long portfolioId);

    /** Dividends/interest per instrument and month + fees/withholdings paid (RF-7). */
    IncomeView income(long portfolioId);

    /** TWR (cumulative) and XIRR (annualized) of the total and per position (RN-8). */
    PerformanceView performance(long portfolioId);

    /** Aggregate wealth of every portfolio in EUR, for the dashboard card (RF-10). */
    InvestmentsSummaryView globalSummary();
}
