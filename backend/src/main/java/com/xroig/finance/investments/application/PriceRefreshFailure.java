package com.xroig.finance.investments.application;

/**
 * One security whose price could not be refreshed (§2.4 of docs/plan/precios.md):
 * either it has no market configured to resolve against the provider, or the
 * provider itself returned no usable quote (unmapped symbol, no data, timeout or
 * exhausted quota — {@link com.xroig.finance.investments.domain.PriceProviderPort}
 * collapses all of those into "no quote", logged as WARN by the adapter).
 */
public record PriceRefreshFailure(long securityId, String ticker, String reason) {
}
