package com.xroig.finance.investments.infrastructure.prices;

import com.xroig.finance.investments.domain.PriceProviderPort;
import com.xroig.finance.investments.domain.PriceQuote;
import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.TwelveDataExchangeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Adapter of {@link PriceProviderPort} against the Twelve Data EOD endpoint
 * (docs/plan/precios.md §2.1). On-demand, closing price only — no real-time,
 * no scheduler (§1). Tolerant by design: an unmapped market, a symbol the
 * provider doesn't know, a timeout or an exhausted daily quota all resolve to
 * an empty list, never an exception — one instrument's failure never blocks
 * the rest of the catalogue's refresh (same principle as the Flex import).
 */
public class TwelveDataPriceProvider implements PriceProviderPort {

    /** Business-tier logger (RN-4 of docs/prd/observabilidad.md): "business.<contexto>". */
    private static final Logger businessLog = LoggerFactory.getLogger("business.investments");

    /**
     * Literals Twelve Data uses for LSE prices quoted in pence rather than
     * pounds (§2.3). Case matters: {@code GBP} (real pounds) must never match
     * here. Not verified against the live API (no API key available at
     * implementation time) — logged as WARN whenever a response currency
     * mismatches {@link Security#currency()} outside this known set, so a new
     * literal or a new pence-quoted market surfaces instead of silently
     * discarding quotes.
     */
    private static final Set<String> PENCE_CURRENCY_LITERALS = Set.of("GBX", "GBp");
    private static final String POUNDS_STERLING = "GBP";
    private static final BigDecimal PENCE_PER_POUND = BigDecimal.valueOf(100);

    private final RestClient restClient;
    private final TwelveDataExchangeResolver resolver;
    private final String apiKey;

    public TwelveDataPriceProvider(RestClient restClient, TwelveDataExchangeResolver resolver, String apiKey) {
        this.restClient = restClient;
        this.resolver = resolver;
        this.apiKey = apiKey;
    }

    @Override
    public List<PriceQuote> latestQuotes(Security security) {
        Optional<String> micCode = resolver.micCodeFor(security.exchange());
        if (micCode.isEmpty() || security.ticker() == null || security.ticker().isBlank()) {
            return List.of();
        }
        try {
            TwelveDataEodResponse response = restClient.get()
                    .uri("https://api.twelvedata.com/eod?symbol={symbol}&mic_code={mic}&apikey={key}",
                            security.ticker(), micCode.get(), apiKey)
                    .retrieve()
                    .body(TwelveDataEodResponse.class);
            return toQuotes(security, response);
        } catch (RestClientException e) {
            logFailure(security, e.getMessage());
            return List.of();
        }
    }

    private List<PriceQuote> toQuotes(Security security, TwelveDataEodResponse response) {
        if (response == null || "error".equalsIgnoreCase(response.status())
                || response.close() == null || response.datetime() == null) {
            logFailure(security, response == null ? "respuesta vacía" : response.message());
            return List.of();
        }
        BigDecimal price = new BigDecimal(response.close());
        if (isPenceForPounds(response.currency(), security.currency())) {
            price = price.divide(PENCE_PER_POUND);
        } else if (response.currency() != null && !response.currency().equalsIgnoreCase(security.currency())) {
            logCurrencyMismatch(security, response.currency());
            return List.of();
        }
        return List.of(PriceQuote.of(security.id(), LocalDate.parse(response.datetime()), price));
    }

    private boolean isPenceForPounds(String responseCurrency, String securityCurrency) {
        return responseCurrency != null
                && PENCE_CURRENCY_LITERALS.contains(responseCurrency)
                && POUNDS_STERLING.equals(securityCurrency);
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
