package com.xroig.finance.investments.application;

import com.xroig.finance.investments.domain.InvestmentTransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read model of one operation for the listing and the manual-entry form (RF-2):
 * the aggregate's fields flattened, each money with its own currency (a null
 * fee/tax currency was normalized to the entry's on construction), plus the
 * instrument's name for the UI. {@code externalId} distinguishes imported rows
 * (RN-10) from manual ones (null).
 */
public record InvestmentTransactionView(long id,
                                        InvestmentTransactionType type,
                                        LocalDate tradeDate,
                                        Long securityId,
                                        String securityName,
                                        BigDecimal quantity,
                                        BigDecimal price,
                                        BigDecimal amount,
                                        String currency,
                                        BigDecimal counterAmount,
                                        String counterCurrency,
                                        BigDecimal fee,
                                        String feeCurrency,
                                        BigDecimal tax,
                                        String taxCurrency,
                                        BigDecimal fxRateToBase,
                                        String description,
                                        String externalId) {
}
