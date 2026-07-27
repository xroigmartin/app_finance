package com.xroig.finance.investments.domain;

import java.util.List;

/**
 * Outbound port for an external price source (Yahoo Finance or another), defined
 * since v1 so the backlog integration (F4) is just a new adapter (§2). It will use
 * the security's {@code exchange}/{@code figi} metadata as the query key (§9).
 * No adapter exists in v1: quotes come exclusively from the Flex import.
 */
public interface PriceProviderPort {

    /** Latest available quotes for the security; empty when the provider has none. */
    List<PriceQuote> latestQuotes(Security security);
}
