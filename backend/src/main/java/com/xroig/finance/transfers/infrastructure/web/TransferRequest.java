package com.xroig.finance.transfers.infrastructure.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Inbound web DTO for creating/editing a transfer. Bean validation rejects a missing
 * date/amount/account or a non-positive amount with 400; the distinct-ends rule is a
 * domain invariant (→ 400 via {@code DomainExceptionHandler}).
 */
public record TransferRequest(@NotNull LocalDate date,
                              @NotNull @Positive BigDecimal amount,
                              String description,
                              @NotNull Long fromAccountId,
                              @NotNull Long toAccountId) {
}
