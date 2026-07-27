package com.xroig.finance.investments.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Closing price parsed from the Flex report's open positions ({@code markPrice}
 * at the statement's {@code toDate}, §9): the v1 source of quotes. Carries the
 * instrument identity + metadata so the import can upsert the quote (RN-9) after
 * resolving the security.
 */
public record FlexQuote(FlexInstrument instrument, BigDecimal price, LocalDate quoteDate) {
}
