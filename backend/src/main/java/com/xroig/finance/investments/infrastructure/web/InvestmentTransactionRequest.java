package com.xroig.finance.investments.infrastructure.web;

import com.xroig.finance.investments.application.port.CreateInvestmentTransaction.InvestmentTransactionCommand;
import com.xroig.finance.investments.domain.InvestmentTransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Inbound web DTO of a manual operation (RF-2). Only the structural minimum is
 * validated here (type, date, amount and its currency); every business invariant
 * (§3 signs, conditional fields, RN-4) lives in the domain and surfaces as 400.
 */
public record InvestmentTransactionRequest(@NotNull InvestmentTransactionType type,
                                           @NotNull LocalDate tradeDate,
                                           Long securityId,
                                           BigDecimal quantity,
                                           BigDecimal price,
                                           @NotNull BigDecimal amount,
                                           @NotBlank String currency,
                                           BigDecimal counterAmount,
                                           String counterCurrency,
                                           BigDecimal fee,
                                           String feeCurrency,
                                           BigDecimal tax,
                                           String taxCurrency,
                                           BigDecimal fxRateToBase,
                                           String description) {

    InvestmentTransactionCommand toCommand() {
        return new InvestmentTransactionCommand(securityId, type, tradeDate, quantity, price,
                amount, currency, counterAmount, counterCurrency, fee, feeCurrency,
                tax, taxCurrency, fxRateToBase, description);
    }
}
