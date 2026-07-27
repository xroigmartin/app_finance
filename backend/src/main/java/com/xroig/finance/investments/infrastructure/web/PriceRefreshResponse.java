package com.xroig.finance.investments.infrastructure.web;

import com.xroig.finance.investments.application.PriceRefreshResult;

import java.util.List;

/** Outbound web DTO: the JSON shape of a price refresh's outcome (§2.6). */
public record PriceRefreshResponse(int updated, List<Failure> failed) {

    public record Failure(Long securityId, String ticker, String reason) {
    }

    public static PriceRefreshResponse from(PriceRefreshResult result) {
        return new PriceRefreshResponse(result.updated(), result.failed().stream()
                .map(f -> new Failure(f.securityId(), f.ticker(), f.reason()))
                .toList());
    }
}
