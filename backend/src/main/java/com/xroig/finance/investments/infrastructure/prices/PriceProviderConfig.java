package com.xroig.finance.investments.infrastructure.prices;

import com.xroig.finance.investments.domain.PriceProviderPort;
import com.xroig.finance.investments.domain.YahooExchangeResolver;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Wires the {@link PriceProviderPort} to the Yahoo Finance adapter
 * (docs/plan/precios.md, revisión 2026-07-26 §2.1): no API key, just a
 * browser {@code User-Agent} on every request — the endpoint doesn't need
 * the cookie+crumb dance, only rejects requests that look like a bare
 * script client. Short timeout so the on-demand refresh button never hangs
 * the request thread if the provider is slow/down.
 */
@Configuration
public class PriceProviderConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    @Bean
    public PriceProviderPort priceProviderPort(RestClient.Builder builder) {
        RestClient restClient = builder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(HttpClientSettings.defaults().withTimeouts(TIMEOUT, TIMEOUT)))
                .defaultHeader(HttpHeaders.USER_AGENT, BROWSER_USER_AGENT)
                .build();
        return new YahooFinancePriceProvider(restClient, new YahooExchangeResolver());
    }
}
