package com.xroig.finance.investments.application;

import com.xroig.finance.investments.application.port.RefreshPrices;
import com.xroig.finance.investments.domain.PriceProviderPort;
import com.xroig.finance.investments.domain.PriceQuote;
import com.xroig.finance.investments.domain.PriceQuoteRepository;
import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.SecurityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Application service for the on-demand price refresh use case (§2.4 of
 * docs/plan/precios.md): walks the instrument catalogue, asks the provider for a
 * fresh quote per security and upserts what comes back. A failure on one
 * instrument never aborts the rest — same tolerant principle as the Flex import.
 */
@Service
@Transactional
public class PriceRefreshService implements RefreshPrices {

    private static final String NO_MARKET_CONFIGURED = "Sin mercado configurado";
    private static final String NO_QUOTE_AVAILABLE = "Sin cotización disponible";

    private final SecurityRepository securities;
    private final PriceProviderPort provider;
    private final PriceQuoteRepository priceQuotes;

    public PriceRefreshService(SecurityRepository securities, PriceProviderPort provider,
                               PriceQuoteRepository priceQuotes) {
        this.securities = securities;
        this.provider = provider;
        this.priceQuotes = priceQuotes;
    }

    @Override
    public PriceRefreshResult refresh() {
        int updated = 0;
        List<PriceRefreshFailure> failed = new ArrayList<>();
        for (Security security : securities.findAll()) {
            if (security.exchange() == null || security.exchange().isBlank()) {
                failed.add(new PriceRefreshFailure(security.id().value(), security.ticker(), NO_MARKET_CONFIGURED));
                continue;
            }
            List<PriceQuote> quotes = provider.latestQuotes(security);
            if (quotes.isEmpty()) {
                failed.add(new PriceRefreshFailure(security.id().value(), security.ticker(), NO_QUOTE_AVAILABLE));
                continue;
            }
            quotes.forEach(priceQuotes::upsert);
            updated++;
        }
        return new PriceRefreshResult(updated, List.copyOf(failed));
    }
}
