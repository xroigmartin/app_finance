package com.xroig.finance.investments.infrastructure.prices;

import com.xroig.finance.investments.domain.PriceProviderPort;
import com.xroig.finance.investments.domain.TwelveDataExchangeResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Wires the {@link PriceProviderPort} to the Twelve Data adapter (§2.1 of
 * docs/plan/precios.md). Short timeout so the on-demand refresh button never
 * hangs the request thread if the provider is slow/down.
 */
@Configuration
public class PriceProviderConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public PriceProviderPort priceProviderPort(RestClient.Builder builder,
                                               @Value("${finance.twelvedata.api-key}") String apiKey) {
        RestClient restClient = builder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(HttpClientSettings.defaults().withTimeouts(TIMEOUT, TIMEOUT)))
                .build();
        return new TwelveDataPriceProvider(restClient, new TwelveDataExchangeResolver(), apiKey);
    }
}
