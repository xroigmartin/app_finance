package com.xroig.finance.investments.infrastructure.prices;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Twelve Data {@code /eod} response, both the happy-path shape ({@code symbol},
 * {@code currency}, {@code datetime}, {@code close}) and the error shape
 * ({@code status="error"}, {@code code}, {@code message}) it returns for an
 * unknown symbol or an exhausted quota — {@code close}/{@code datetime} are then
 * simply absent. Ignores unknown fields (e.g. {@code exchange}, {@code mic_code}):
 * this adapter only needs the price-conversion inputs (§2.3 of docs/plan/precios.md).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TwelveDataEodResponse(String symbol, String currency, String datetime, String close,
                             String status, String message) {
}
