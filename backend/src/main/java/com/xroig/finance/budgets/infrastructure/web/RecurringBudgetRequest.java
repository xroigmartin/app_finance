package com.xroig.finance.budgets.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inbound web DTO of the recurrence sub-resource. Bean validation rejects an empty month or
 * amount list and a non-positive/missing amount with 400; the leaf/scope rules, the month
 * range and the {@code validoDesde} format are resolved in the application/domain (→ 404/400
 * via {@code DomainExceptionHandler}). Months travel as integers 1..12 and {@code validoDesde}
 * as a {@code "YYYY-MM"} string.
 */
public record RecurringBudgetRequest(@NotEmpty List<Integer> months,
                                     boolean active,
                                     @NotEmpty @Valid List<AmountRequest> amounts) {

    public record AmountRequest(@NotNull @Positive BigDecimal amount,
                                @NotNull String validoDesde) {
    }
}
