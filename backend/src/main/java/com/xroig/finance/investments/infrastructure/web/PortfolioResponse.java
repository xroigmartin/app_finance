package com.xroig.finance.investments.infrastructure.web;

import com.xroig.finance.investments.domain.Portfolio;

/** Outbound web DTO: the JSON shape of a portfolio ({@code id, name, baseCurrency}). */
public record PortfolioResponse(Long id, String name, String baseCurrency) {

    /** The controller only ever serializes persisted portfolios, so the identity is always present. */
    public static PortfolioResponse from(Portfolio portfolio) {
        return new PortfolioResponse(portfolio.id().value(), portfolio.name(), portfolio.baseCurrency());
    }
}
