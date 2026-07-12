package com.xroig.finance.investments.application;

import com.xroig.finance.investments.domain.InvestmentTransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One operation parsed from the Flex report, already translated to the domain's
 * language (§3 sign convention — the parser copies the Flex signs almost
 * verbatim) but not yet resolved against persistence: the instrument is a
 * {@link FlexInstrument} by identity, not a {@code SecurityId}. The import use
 * case (H1.10) resolves/registers the security and builds the
 * {@code InvestmentTransaction}, whose invariants re-validate every field.
 * {@code externalId} comes origin-prefixed ({@code ORD-/CT-/FTT-/CA-}, RN-10).
 */
public record FlexRow(InvestmentTransactionType type,
                      String externalId,
                      LocalDate tradeDate,
                      BigDecimal quantity,
                      BigDecimal price,
                      BigDecimal amount,
                      String currency,
                      BigDecimal counterAmount,
                      String counterCurrency,
                      BigDecimal fee,
                      String feeCurrency,
                      BigDecimal fxRateToBase,
                      String description,
                      FlexInstrument instrument) {
}
