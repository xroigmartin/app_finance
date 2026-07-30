package com.xroig.finance.investments.application;

import com.xroig.finance.investments.domain.PriceProviderPort;
import com.xroig.finance.investments.domain.PriceQuote;
import com.xroig.finance.investments.domain.PriceQuoteRepository;
import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.SecurityId;
import com.xroig.finance.investments.domain.SecurityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application-service tests for the on-demand price refresh use case (§2.4 of
 * docs/plan/precios.md): a failure on one security never aborts the rest, and
 * the result aggregates updated/failed counts with a reason per failure.
 */
@ExtendWith(MockitoExtension.class)
class PriceRefreshServiceTest {

    @Mock private SecurityRepository securities;
    @Mock private PriceProviderPort provider;
    @Mock private PriceQuoteRepository priceQuotes;

    private PriceRefreshService service() {
        return new PriceRefreshService(securities, provider, priceQuotes);
    }

    private static Security withExchange(long id, String ticker, String exchange) {
        return Security.rehydrate(new SecurityId(id), "IE00BK5BQT80", "USD", "Instrumento " + ticker,
                ticker, "STOCK", exchange, null);
    }

    @Test
    void oneFailingSecurityDoesNotAbortTheRest() {
        Security ok = withExchange(1L, "AAPL", "NASDAQ");
        Security failing = withExchange(2L, "XYZ", "NASDAQ");
        when(securities.findAll()).thenReturn(List.of(ok, failing));
        when(provider.latestQuotes(ok)).thenReturn(
                List.of(PriceQuote.of(ok.id(), LocalDate.of(2026, 7, 24), "184.92")));
        when(provider.latestQuotes(failing)).thenReturn(List.of());

        PriceRefreshResult result = service().refresh();

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.failed()).hasSize(1);
        assertThat(result.failed().getFirst().securityId()).isEqualTo(2L);
        assertThat(result.failed().getFirst().ticker()).isEqualTo("XYZ");
        verify(priceQuotes).upsert(any(PriceQuote.class));
    }

    @Test
    void securityWithoutExchangeIsReportedAsUnconfiguredWithoutCallingProvider() {
        Security noExchange = withExchange(3L, "NOEX", null);
        when(securities.findAll()).thenReturn(List.of(noExchange));

        PriceRefreshResult result = service().refresh();

        assertThat(result.updated()).isZero();
        assertThat(result.failed()).hasSize(1);
        assertThat(result.failed().getFirst().reason()).isEqualTo("Sin mercado configurado");
        verify(provider, never()).latestQuotes(any());
        verify(priceQuotes, never()).upsert(any());
    }

    @Test
    void emptyCatalogueReturnsZeroedResult() {
        when(securities.findAll()).thenReturn(List.of());

        PriceRefreshResult result = service().refresh();

        assertThat(result.updated()).isZero();
        assertThat(result.failed()).isEmpty();
    }
}
