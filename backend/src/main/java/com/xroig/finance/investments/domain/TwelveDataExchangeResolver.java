package com.xroig.finance.investments.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Translates the IBKR {@code exchange} code captured on {@link Security} (§9 of
 * the PRD) into the MIC code (ISO 10383) Twelve Data expects in its
 * {@code mic_code} query parameter. Only covers the markets actually present in
 * the portfolio today (docs/plan/precios.md §2.2) — extend the table when a new
 * market shows up, don't pre-populate ones the portfolio doesn't have.
 *
 * <p>An unmapped or blank exchange resolves to empty: the caller then skips that
 * security's refresh instead of failing (contract of {@link PriceProviderPort}).
 */
public class TwelveDataExchangeResolver {

    private static final Map<String, String> MIC_CODE_BY_IBKR_EXCHANGE = Map.of(
            "LSE", "XLON",
            "NASDAQ", "XNAS",
            "SBF", "XPAR",
            "AEB", "XAMS",
            "BVME", "XMIL",
            "IBIS", "XETR"
    );

    public Optional<String> micCodeFor(String ibkrExchange) {
        if (ibkrExchange == null || ibkrExchange.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(MIC_CODE_BY_IBKR_EXCHANGE.get(ibkrExchange.toUpperCase(Locale.ROOT)));
    }
}
