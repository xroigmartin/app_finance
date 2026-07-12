package com.xroig.finance.investments.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read model (CQRS) of one point of the evolution chart (§6/§7): the portfolio's
 * value and the cumulative net contributions at that date, in the base currency.
 * Points exist at flow dates ({@code DEPOSIT}/{@code WITHDRAWAL} — the stepped
 * contribution series is exact) and at quote dates (the value series only has
 * points where quotes exist, i.e. import dates until the price API arrives).
 */
public record ValuationHistoryView(LocalDate date, BigDecimal value, BigDecimal contributed) {
}
