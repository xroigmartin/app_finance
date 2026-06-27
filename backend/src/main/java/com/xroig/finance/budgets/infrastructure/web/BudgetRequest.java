package com.xroig.finance.budgets.infrastructure.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Inbound web DTO for creating/editing a budget. Bean validation rejects a missing
 * account/category/year, a month out of 1..12 or a non-positive amount with 400;
 * the duplicate-slot and leaf/scope rules are resolved in the application/domain
 * (→ 409/400 via {@code DomainExceptionHandler}).
 */
public record BudgetRequest(@NotNull Long accountId,
                            @NotNull Long categoryId,
                            @NotNull Integer year,
                            @NotNull @Min(1) @Max(12) Integer month,
                            @NotNull @Positive BigDecimal amount) {
}
