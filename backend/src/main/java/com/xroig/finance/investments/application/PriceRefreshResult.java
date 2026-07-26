package com.xroig.finance.investments.application;

import java.util.List;

/** Outcome of an on-demand price refresh (§2.4 of docs/plan/precios.md). */
public record PriceRefreshResult(int updated, List<PriceRefreshFailure> failed) {
}
