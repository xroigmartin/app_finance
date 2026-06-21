package com.xroig.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTOs of the recurrence sub-resource ({@code /api/categories/{id}/recurrence}).
 * Months travel as a plain list of integers 1..12 (more readable than the bitmask
 * used in storage) and {@code validoDesde} as a {@code "YYYY-MM"} string.
 */
public final class RecurringBudgetDtos {

    private RecurringBudgetDtos() {
    }

    public record AmountRequest(@NotNull @Positive BigDecimal amount,
                                @NotNull String validoDesde) {
    }

    public record RecurringBudgetRequest(@NotEmpty List<Integer> months,
                                         boolean active,
                                         @NotEmpty @Valid List<AmountRequest> amounts) {
    }

    public record AmountResponse(Long id, BigDecimal amount, String validoDesde) {
    }

    public record RecurringBudgetResponse(Long categoryId,
                                          List<Integer> months,
                                          boolean active,
                                          List<AmountResponse> amounts) {
    }
}
