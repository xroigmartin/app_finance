package com.xroig.finance.reporting.application;

import java.math.BigDecimal;
import java.util.List;

/** Read model (CQRS): per-account monthly income/expense series for cross-account comparison. */
public record AccountComparisonView(List<String> months, List<AccountSeriesView> accounts) {

    /** The monthly income/expense series of a single account. */
    public record AccountSeriesView(Long accountId, String name,
                                    List<BigDecimal> income, List<BigDecimal> expense) {
    }
}
