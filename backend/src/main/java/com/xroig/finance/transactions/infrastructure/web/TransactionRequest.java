package com.xroig.finance.transactions.infrastructure.web;

import com.xroig.finance.shared.domain.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Inbound web DTO for creating/editing a movement. Bean validation rejects a missing
 * date/amount/type/account/category or a non-positive amount with 400. When {@code
 * refundOfId} is set the movement is a refund (type/account/category are inherited).
 */
public record TransactionRequest(@NotNull LocalDate date,
                                 @NotNull @Positive BigDecimal amount,
                                 String description,
                                 @NotNull TransactionType type,
                                 @NotNull Long accountId,
                                 @NotNull Long categoryId,
                                 Long refundOfId) {
}
