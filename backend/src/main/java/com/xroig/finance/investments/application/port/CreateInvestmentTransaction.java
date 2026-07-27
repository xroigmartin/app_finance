package com.xroig.finance.investments.application.port;

import com.xroig.finance.investments.application.InvestmentTransactionView;
import com.xroig.finance.investments.domain.InvestmentTransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Inbound port: register a manual operation in a portfolio (RF-2). The §3 sign
 * invariants live in the aggregate (violation → 400, §8); a manual sale beyond
 * the held position at its date is rejected (RN-4's hard side). Manual entries
 * carry no {@code external_id} (RN-10).
 */
public interface CreateInvestmentTransaction {

    InvestmentTransactionView create(long portfolioId, InvestmentTransactionCommand command);

    /**
     * Fields of a manual operation. Currencies accompany their amounts; a null
     * fee/tax currency defaults to the entry's {@code currency}.
     */
    record InvestmentTransactionCommand(Long securityId,
                                        InvestmentTransactionType type,
                                        LocalDate tradeDate,
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
                                        String description) {
    }
}
