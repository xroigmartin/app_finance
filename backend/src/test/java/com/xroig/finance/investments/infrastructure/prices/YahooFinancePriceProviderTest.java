package com.xroig.finance.investments.infrastructure.prices;

import com.xroig.finance.investments.domain.PriceQuote;
import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.SecurityId;
import com.xroig.finance.investments.domain.YahooExchangeResolver;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link YahooFinancePriceProvider} against a fake HTTP server
 * ({@link MockRestServiceServer}), per docs/plan/precios.md (revisión
 * 2026-07-26, checkpoints "Nuevo adaptador Yahoo Finance"): happy path, the
 * GBp/peniques -> GBP conversion (§2.3), symbol-not-found, timeout, unmapped
 * market and an unexpected currency mismatch. Response fixtures below are
 * trimmed real payloads captured from {@code query1.finance.yahoo.com} on
 * 2026-07-26 (LSE returns "GBp", lowercase p).
 */
class YahooFinancePriceProviderTest {

    private static final Security ZEG = Security.rehydrate(new SecurityId(1L), "GB0031743007", "GBP",
            "Zegona Communications", "ZEG", "STOCK", "LSE", null);
    private static final Security APPLE = Security.rehydrate(new SecurityId(2L), "US0378331005", "USD",
            "Apple Inc", "AAPL", "STOCK", "NASDAQ", null);

    private static final String CHART_URI = "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=5d&interval=1d";

    @Test
    void returnsQuoteConvertedFromPenceToPoundsForLondonStockExchange() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestToUriTemplate(CHART_URI, "ZEG.L"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"chart":{"result":[{"meta":{"currency":"GBp","gmtoffset":3600},
                        "timestamp":[1784530800,1784617200,1784703600,1784790000,1784876400],
                        "indicators":{"quote":[{"close":[1484.0,1502.0,1520.0,1480.0,1500.0]}]}}],"error":null}}
                        """, MediaType.APPLICATION_JSON));

        YahooFinancePriceProvider provider = new YahooFinancePriceProvider(builder.build(), new YahooExchangeResolver());

        List<PriceQuote> quotes = provider.latestQuotes(ZEG);

        assertThat(quotes).hasSize(1);
        assertThat(quotes.getFirst().price()).isEqualByComparingTo("15");
        assertThat(quotes.getFirst().quoteDate()).isEqualTo(LocalDate.of(2026, 7, 24));
    }

    @Test
    void returnsQuoteUnconvertedForNonPenceCurrencyAndSkipsTrailingNullClose() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestToUriTemplate(CHART_URI, "AAPL"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"chart":{"result":[{"meta":{"currency":"USD","gmtoffset":-14400},
                        "timestamp":[1784554200,1784640600,1784727000,1784813400,1784899800],
                        "indicators":{"quote":[{"close":[326.59,327.73,325.89,321.66,null]}]}}],"error":null}}
                        """, MediaType.APPLICATION_JSON));

        YahooFinancePriceProvider provider = new YahooFinancePriceProvider(builder.build(), new YahooExchangeResolver());

        List<PriceQuote> quotes = provider.latestQuotes(APPLE);

        assertThat(quotes).hasSize(1);
        assertThat(quotes.getFirst().price()).isEqualByComparingTo("321.66");
        assertThat(quotes.getFirst().quoteDate()).isEqualTo(LocalDate.of(2026, 7, 23));
    }

    @Test
    void returnsEmptyListWhenSymbolNotFound() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestToUriTemplate(CHART_URI, "AAPL"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("""
                        {"chart":{"result":null,"error":{"code":"Not Found","description":"No data found, symbol may be delisted"}}}
                        """).contentType(MediaType.APPLICATION_JSON));

        YahooFinancePriceProvider provider = new YahooFinancePriceProvider(builder.build(), new YahooExchangeResolver());

        assertThat(provider.latestQuotes(APPLE)).isEmpty();
    }

    @Test
    void returnsEmptyListOnTimeout() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestToUriTemplate(CHART_URI, "AAPL"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("timeout");
                });

        YahooFinancePriceProvider provider = new YahooFinancePriceProvider(builder.build(), new YahooExchangeResolver());

        assertThat(provider.latestQuotes(APPLE)).isEmpty();
    }

    @Test
    void returnsEmptyListWithoutCallingProviderWhenExchangeIsUnmapped() {
        Security unmapped = Security.rehydrate(new SecurityId(3L), "US0000000000", "USD",
                "Instrumento sin mercado mapeado", "XYZ", "STOCK", "EXCHANGE_NO_MAPEADO", null);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        YahooFinancePriceProvider provider = new YahooFinancePriceProvider(builder.build(), new YahooExchangeResolver());

        assertThat(provider.latestQuotes(unmapped)).isEmpty();
        server.verify();
    }

    @Test
    void returnsEmptyListWhenResponseCurrencyMismatchesSecurityCurrency() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestToUriTemplate(CHART_URI, "AAPL"))
                .andRespond(withSuccess("""
                        {"chart":{"result":[{"meta":{"currency":"EUR","gmtoffset":-14400},
                        "timestamp":[1784899800],
                        "indicators":{"quote":[{"close":[184.92]}]}}],"error":null}}
                        """, MediaType.APPLICATION_JSON));

        YahooFinancePriceProvider provider = new YahooFinancePriceProvider(builder.build(), new YahooExchangeResolver());

        assertThat(provider.latestQuotes(APPLE)).isEmpty();
    }
}
