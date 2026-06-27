package com.xroig.finance.reporting.application;

import java.math.BigDecimal;

/** Read model (CQRS): income and expense of a single month, for the evolution chart. */
public record MonthlyPointView(String month, BigDecimal income, BigDecimal expense) {
}
