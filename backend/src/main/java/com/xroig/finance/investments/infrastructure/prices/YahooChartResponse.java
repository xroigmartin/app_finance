package com.xroig.finance.investments.infrastructure.prices;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Yahoo Finance {@code /v8/finance/chart/{symbol}} response, trimmed to the
 * fields this adapter needs (docs/plan/precios.md, revisión 2026-07-26):
 * closing prices for the last few sessions plus the currency and UTC offset
 * of the exchange. A symbol Yahoo doesn't know returns HTTP 404 with
 * {@code result: null} instead of this shape — handled upstream as a
 * {@code RestClientException}, never reaching this record. Ignores unknown
 * fields (regularMarketPrice, exchangeName, adjclose…): only the EOD close
 * series is relevant here, never a live/intraday price (§1 of the plan).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record YahooChartResponse(Chart chart) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Chart(List<Result> result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(Meta meta, List<Long> timestamp, Indicators indicators) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Meta(String currency, Integer gmtoffset) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Indicators(List<Quote> quote) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Quote(List<BigDecimal> close) {
    }
}
