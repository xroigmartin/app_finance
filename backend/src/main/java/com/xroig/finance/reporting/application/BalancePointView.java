package com.xroig.finance.reporting.application;

import java.math.BigDecimal;

/** Read model (CQRS): net worth (balance) at the end of a given month. */
public record BalancePointView(String month, BigDecimal balance) {
}
