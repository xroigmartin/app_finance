package com.xroig.finance.investments.application;

import com.xroig.finance.investments.domain.ExchangeRate;

import java.time.LocalDate;
import java.util.List;

/**
 * A parsed IBKR Activity Flex report (§9): the account identity and its base
 * currency (validated against the portfolio's before importing, §8), the
 * operation rows in domain language, the quotes from the open positions, the
 * conversion rates already normalized currency→EUR and filtered to the report's
 * currencies, and the per-row errors of unsupported/unreadable rows.
 */
public record FlexReport(String accountId,
                         String baseCurrency,
                         LocalDate fromDate,
                         LocalDate toDate,
                         List<FlexRow> rows,
                         List<FlexQuote> quotes,
                         List<ExchangeRate> exchangeRates,
                         List<FlexRowError> errors) {
}
