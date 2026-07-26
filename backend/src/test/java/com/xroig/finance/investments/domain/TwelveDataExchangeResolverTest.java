package com.xroig.finance.investments.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TwelveDataExchangeResolver}: translates the IBKR
 * {@code exchange} code stored on {@link Security} (§9 of the PRD, e.g. "LSE",
 * "NASDAQ") into the MIC code (ISO 10383) Twelve Data expects in its
 * {@code mic_code} query parameter. Unmapped/blank exchanges resolve to empty
 * (§2.2 of docs/plan/precios.md): an instrument outside the mapped markets
 * simply doesn't refresh, it never breaks the rest of the catalogue.
 */
class TwelveDataExchangeResolverTest {

    private final TwelveDataExchangeResolver resolver = new TwelveDataExchangeResolver();

    @Test
    void resolvesLondonStockExchangeMicCode() {
        assertThat(resolver.micCodeFor("LSE")).contains("XLON");
    }

    @Test
    void resolvesNasdaqMicCode() {
        assertThat(resolver.micCodeFor("NASDAQ")).contains("XNAS");
    }

    @Test
    void returnsEmptyForUnknownExchange() {
        assertThat(resolver.micCodeFor("NYSE_ARCA_DESCONOCIDO")).isEqualTo(Optional.empty());
    }

    @Test
    void returnsEmptyForNullExchange() {
        assertThat(resolver.micCodeFor(null)).isEqualTo(Optional.empty());
    }

    @Test
    void returnsEmptyForBlankExchange() {
        assertThat(resolver.micCodeFor("   ")).isEqualTo(Optional.empty());
    }

    @Test
    void resolvesRemainingPortfolioExchanges() {
        assertThat(resolver.micCodeFor("SBF")).contains("XPAR");
        assertThat(resolver.micCodeFor("AEB")).contains("XAMS");
        assertThat(resolver.micCodeFor("BVME")).contains("XMIL");
        assertThat(resolver.micCodeFor("IBIS")).contains("XETR");
    }

    @Test
    void resolutionIsCaseInsensitive() {
        assertThat(resolver.micCodeFor("lse")).contains("XLON");
    }
}
