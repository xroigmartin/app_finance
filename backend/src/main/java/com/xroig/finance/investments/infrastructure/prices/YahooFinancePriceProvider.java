package com.xroig.finance.investments.infrastructure.prices;

import com.xroig.finance.investments.domain.PriceProviderPort;
import com.xroig.finance.investments.domain.PriceQuote;
import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.YahooExchangeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Adapter of {@link PriceProviderPort} against the unofficial Yahoo Finance
 * chart endpoint (docs/plan/precios.md, revisión 2026-07-26): no cookie/crumb
 * needed, just a browser {@code User-Agent} (wired in {@link PriceProviderConfig}).
 * On-demand, closing price only — the last non-null close in the response's
 * daily series, never {@code regularMarketPrice} (which can be a live,
 * intraday quote while the market is open). Tolerant by design: an unmapped
 * market, a symbol the provider doesn't know, a timeout or a malformed
 * response all resolve to an empty list, never an exception — one
 * instrument's failure never blocks the rest of the catalogue's refresh
 * (same principle as the Flex import).
 */
public class YahooFinancePriceProvider implements PriceProviderPort {

    /** Business-tier logger (RN-4 of docs/prd/observabilidad.md): "business.<contexto>". */
    private static final Logger businessLog = LoggerFactory.getLogger("business.investments");

    /**
     * Literal Yahoo uses for LSE prices quoted in pence rather than pounds
     * (§2.3), confirmed empirically against the live endpoint on 2026-07-26
     * (lowercase {@code p} — {@code "GBp"}, never {@code "GBP"}, which is real
     * pounds and must never match here).
     */
    private static final String PENCE_CURRENCY_LITERAL = "GBp";
    private static final String POUNDS_STERLING = "GBP";
    private static final BigDecimal PENCE_PER_POUND = BigDecimal.valueOf(100);
    private static final String CHART_URI =
            "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=5d&interval=1d";

    private final RestClient restClient;
    private final YahooExchangeResolver resolver;

    public YahooFinancePriceProvider(RestClient restClient, YahooExchangeResolver resolver) {
        this.restClient = restClient;
        this.resolver = resolver;
    }

    @Override
    public List<PriceQuote> latestQuotes(Security security) {
        Optional<String> symbol = resolver.symbolFor(security.ticker(), security.exchange());
        if (symbol.isEmpty()) {
            return List.of();
        }
        try {
            YahooChartResponse response = restClient.get()
                    .uri(CHART_URI, symbol.get())
                    .retrieve()
                    .body(YahooChartResponse.class);
            return toQuotes(security, response);
        } catch (RestClientException e) {
            logFailure(security, e.getMessage());
            return List.of();
        }
    }

    private List<PriceQuote> toQuotes(Security security, YahooChartResponse response) {
        YahooChartResponse.Result result = firstResult(response);
        if (result == null || result.meta() == null || result.timestamp() == null || result.indicators() == null) {
            logFailure(security, "respuesta vacía");
            return List.of();
        }
        List<BigDecimal> closes = closesOf(result);
        int lastIndex = lastNonNullIndex(closes);
        if (lastIndex < 0 || lastIndex >= result.timestamp().size()) {
            logFailure(security, "sin cierre disponible");
            return List.of();
        }

        BigDecimal price = closes.get(lastIndex);
        String responseCurrency = result.meta().currency();
        if (isPenceForPounds(responseCurrency, security.currency())) {
            price = price.divide(PENCE_PER_POUND);
        } else if (responseCurrency != null && !responseCurrency.equalsIgnoreCase(security.currency())) {
            logCurrencyMismatch(security, responseCurrency);
            return List.of();
        }

        LocalDate date = dateOf(result, lastIndex);
        return List.of(PriceQuote.of(security.id(), date, price));
    }

    private YahooChartResponse.Result firstResult(YahooChartResponse response) {
        if (response == null || response.chart() == null || response.chart().result() == null
                || response.chart().result().isEmpty()) {
            return null;
        }
        return response.chart().result().getFirst();
    }

    private List<BigDecimal> closesOf(YahooChartResponse.Result result) {
        List<YahooChartResponse.Quote> quotes = result.indicators().quote();
        if (quotes == null || quotes.isEmpty() || quotes.getFirst().close() == null) {
            return List.of();
        }
        return quotes.getFirst().close();
    }

    private int lastNonNullIndex(List<BigDecimal> closes) {
        for (int i = closes.size() - 1; i >= 0; i--) {
            if (closes.get(i) != null) {
                return i;
            }
        }
        return -1;
    }

    private LocalDate dateOf(YahooChartResponse.Result result, int index) {
        long epochSeconds = result.timestamp().get(index);
        int gmtOffsetSeconds = result.meta().gmtoffset() == null ? 0 : result.meta().gmtoffset();
        return Instant.ofEpochSecond(epochSeconds + gmtOffsetSeconds).atZone(ZoneOffset.UTC).toLocalDate();
    }

    private boolean isPenceForPounds(String responseCurrency, String securityCurrency) {
        return PENCE_CURRENCY_LITERAL.equals(responseCurrency) && POUNDS_STERLING.equals(securityCurrency);
    }

    private void logFailure(Security security, String reason) {
        businessLog.atWarn()
                .setMessage("price_refresh_failed")
                .addKeyValue("action", "RefreshPrices")
                .addKeyValue("security_id", security.id() == null ? null : security.id().value())
                .addKeyValue("ticker", security.ticker())
                .addKeyValue("exchange", security.exchange())
                .addKeyValue("reason", reason)
                .log();
    }

    private void logCurrencyMismatch(Security security, String responseCurrency) {
        businessLog.atWarn()
                .setMessage("price_refresh_currency_mismatch")
                .addKeyValue("action", "RefreshPrices")
                .addKeyValue("security_id", security.id() == null ? null : security.id().value())
                .addKeyValue("ticker", security.ticker())
                .addKeyValue("security_currency", security.currency())
                .addKeyValue("response_currency", responseCurrency)
                .log();
    }
}
