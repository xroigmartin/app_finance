package com.xroig.finance.investments.infrastructure.web;

import com.xroig.finance.investments.domain.Security;

/** Outbound web DTO: the JSON shape of an instrument (identity + metadata, §3). */
public record SecurityResponse(Long id, String isin, String currency, String name,
                               String ticker, String type, String exchange, String figi) {

    /** The controller only ever serializes persisted instruments, so the identity is always present. */
    public static SecurityResponse from(Security security) {
        return new SecurityResponse(security.id().value(), security.isin(), security.currency(),
                security.name(), security.ticker(), security.type(), security.exchange(), security.figi());
    }
}
