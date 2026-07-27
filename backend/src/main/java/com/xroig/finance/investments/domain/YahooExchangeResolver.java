package com.xroig.finance.investments.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the Yahoo Finance chart symbol (ticker + market suffix, e.g.
 * {@code ZEG.L}) from the IBKR {@code exchange} code captured on
 * {@link Security} (§9 of the PRD). Table verified empirically against the
 * live endpoint (docs/plan/precios.md, revisión 2026-07-26) — only covers the
 * markets actually present in the portfolio today, extend it when a new
 * market shows up, don't pre-populate ones the portfolio doesn't have.
 *
 * <p>An unmapped/blank exchange or a blank/null ticker resolves to empty: the
 * caller then skips that security's refresh instead of failing (contract of
 * {@link PriceProviderPort}).
 */
public class YahooExchangeResolver {

    private static final Map<String, String> SUFFIX_BY_IBKR_EXCHANGE = Map.of(
            "NASDAQ", "",
            "LSE", ".L",
            "IBIS", ".DE",
            "SBF", ".PA",
            "AEB", ".AS",
            "BVME", ".MI"
    );

    public Optional<String> symbolFor(String ticker, String ibkrExchange) {
        if (ticker == null || ticker.isBlank()) {
            return Optional.empty();
        }
        if (ibkrExchange == null || ibkrExchange.isBlank()) {
            return Optional.empty();
        }
        String suffix = SUFFIX_BY_IBKR_EXCHANGE.get(ibkrExchange.toUpperCase(Locale.ROOT));
        if (suffix == null) {
            return Optional.empty();
        }
        return Optional.of(ticker + suffix);
    }
}
