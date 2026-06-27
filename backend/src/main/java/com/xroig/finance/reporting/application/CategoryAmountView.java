package com.xroig.finance.reporting.application;

import java.math.BigDecimal;

/** Read model (CQRS): the net amount of a (top-level) category in a period, with its color. */
public record CategoryAmountView(String category, String color, BigDecimal amount) {
}
