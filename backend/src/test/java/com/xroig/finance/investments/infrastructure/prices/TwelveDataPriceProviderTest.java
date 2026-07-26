package com.xroig.finance.investments.infrastructure.prices;

import com.xroig.finance.investments.domain.PriceQuote;
import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.SecurityId;
import com.xroig.finance.investments.domain.TwelveDataExchangeResolver;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link TwelveDataPriceProvider} against a fake HTTP server
 * ({@link MockRestServiceServer}), per docs/plan/precios.md M2/M3: happy path,
 * the GBX/peniques -> GBP conversion (§2.3) and its negative (USD untouched).
 */
class TwelveDataPriceProviderTest {

    private static final Security ZEG = Security.rehydrate(new SecurityId(1L), "GB0031743007", "GBP",
            "Zegona Communications", "ZEG", "STOCK", "LSE", null);
    private static final Security APPLE = Security.rehydrate(new SecurityId(2L), "US0378331005", "USD",
            "Apple Inc", "AAPL", "STOCK", "NASDAQ", null);

    @Test
    void returnsQuoteConvertedFromPenceToPoundsForLondonStockExchange() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestToUriTemplate(
                        "https://api.twelvedata.com/eod?symbol={symbol}&mic_code={mic}&apikey={key}",
                        "ZEG", "XLON", "test-key"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"symbol":"ZEG","currency":"GBX","datetime":"2026-07-24","close":"133.50"}
                        """, MediaType.APPLICATION_JSON));

        TwelveDataPriceProvider provider = new TwelveDataPriceProvider(
                builder.build(), new TwelveDataExchangeResolver(), "test-key");

        List<PriceQuote> quotes = provider.latestQuotes(ZEG);

        assertThat(quotes).hasSize(1);
        assertThat(quotes.getFirst().price()).isEqualByComparingTo("1.3350");
    }

    @Test
    void returnsQuoteUnconvertedForNonPenceCurrency() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestToUriTemplate(
                        "https://api.twelvedata.com/eod?symbol={symbol}&mic_code={mic}&apikey={key}",
                        "AAPL", "XNAS", "test-key"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"symbol":"AAPL","currency":"USD","datetime":"2026-07-24","close":"184.92"}
                        """, MediaType.APPLICATION_JSON));

        TwelveDataPriceProvider provider = new TwelveDataPriceProvider(
                builder.build(), new TwelveDataExchangeResolver(), "test-key");

        List<PriceQuote> quotes = provider.latestQuotes(APPLE);

        assertThat(quotes).hasSize(1);
        assertThat(quotes.getFirst().price()).isEqualByComparingTo("184.92");
    }

    @Test
    void returnsEmptyListWhenSymbolNotFound() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestToUriTemplate(
                        "https://api.twelvedata.com/eod?symbol={symbol}&mic_code={mic}&apikey={key}",
                        "AAPL", "XNAS", "test-key"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("""
                        {"code":404,"message":"symbol not found","status":"error"}
                        """).contentType(MediaType.APPLICATION_JSON));

        TwelveDataPriceProvider provider = new TwelveDataPriceProvider(
                builder.build(), new TwelveDataExchangeResolver(), "test-key");

        assertThat(provider.latestQuotes(APPLE)).isEmpty();
    }

    @Test
    void returnsEmptyListOnTimeout() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestToUriTemplate(
                        "https://api.twelvedata.com/eod?symbol={symbol}&mic_code={mic}&apikey={key}",
                        "AAPL", "XNAS", "test-key"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("timeout");
                });

        TwelveDataPriceProvider provider = new TwelveDataPriceProvider(
                builder.build(), new TwelveDataExchangeResolver(), "test-key");

        assertThat(provider.latestQuotes(APPLE)).isEmpty();
    }

    @Test
    void returnsEmptyListWhenDailyQuotaExhausted() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestToUriTemplate(
                        "https://api.twelvedata.com/eod?symbol={symbol}&mic_code={mic}&apikey={key}",
                        "AAPL", "XNAS", "test-key"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("""
                        {"code":429,"message":"API credits exhausted","status":"error"}
                        """).contentType(MediaType.APPLICATION_JSON));

        TwelveDataPriceProvider provider = new TwelveDataPriceProvider(
                builder.build(), new TwelveDataExchangeResolver(), "test-key");

        assertThat(provider.latestQuotes(APPLE)).isEmpty();
    }

    @Test
    void returnsEmptyListWithoutCallingProviderWhenExchangeIsUnmapped() {
        Security unmapped = Security.rehydrate(new SecurityId(3L), "US0000000000", "USD",
                "Instrumento sin mercado mapeado", "XYZ", "STOCK", "EXCHANGE_NO_MAPEADO", null);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        TwelveDataPriceProvider provider = new TwelveDataPriceProvider(
                builder.build(), new TwelveDataExchangeResolver(), "test-key");

        assertThat(provider.latestQuotes(unmapped)).isEmpty();
        server.verify();
    }
}
