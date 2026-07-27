package com.xroig.finance.investments.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link YahooExchangeResolver}: builds the Yahoo Finance chart
 * symbol (ticker + market suffix) from the IBKR {@code exchange} code stored on
 * {@link Security} (§9 of the PRD), per the table verified empirically in
 * docs/plan/precios.md (revisión 2026-07-26). Unmapped/blank exchange or
 * blank/null ticker resolves to empty: the caller then skips that security's
 * refresh instead of failing (contract of {@link PriceProviderPort}).
 */
class YahooExchangeResolverTest {

    private final YahooExchangeResolver resolver = new YahooExchangeResolver();

    @Test
    void resolvesLondonStockExchangeSuffix() {
        assertThat(resolver.symbolFor("ZEG", "LSE")).contains("ZEG.L");
    }

    @Test
    void resolvesNasdaqWithoutSuffix() {
        assertThat(resolver.symbolFor("AAPL", "NASDAQ")).contains("AAPL");
    }

    @Test
    void returnsEmptyForUnknownExchange() {
        assertThat(resolver.symbolFor("XYZ", "NYSE_ARCA_DESCONOCIDO")).isEqualTo(Optional.empty());
    }

    @Test
    void returnsEmptyForNullExchange() {
        assertThat(resolver.symbolFor("AAPL", null)).isEqualTo(Optional.empty());
    }

    @Test
    void returnsEmptyForBlankExchange() {
        assertThat(resolver.symbolFor("AAPL", "   ")).isEqualTo(Optional.empty());
    }

    @Test
    void returnsEmptyForNullTicker() {
        assertThat(resolver.symbolFor(null, "NASDAQ")).isEqualTo(Optional.empty());
    }

    @Test
    void returnsEmptyForBlankTicker() {
        assertThat(resolver.symbolFor("   ", "NASDAQ")).isEqualTo(Optional.empty());
    }

    @Test
    void resolvesRemainingPortfolioExchanges() {
        assertThat(resolver.symbolFor("SGO", "SBF")).contains("SGO.PA");
        assertThat(resolver.symbolFor("ASML", "AEB")).contains("ASML.AS");
        assertThat(resolver.symbolFor("ENEL", "BVME")).contains("ENEL.MI");
        assertThat(resolver.symbolFor("SAP", "IBIS")).contains("SAP.DE");
    }

    @Test
    void resolutionIsCaseInsensitive() {
        assertThat(resolver.symbolFor("ZEG", "lse")).contains("ZEG.L");
    }
}
