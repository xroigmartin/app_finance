package com.xroig.finance.budgets.infrastructure.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Inbound web DTO for copying the budgets of one month into another. */
public record CopyRequest(@NotNull Integer fromYear,
                          @NotNull @Min(1) @Max(12) Integer fromMonth,
                          @NotNull Integer toYear,
                          @NotNull @Min(1) @Max(12) Integer toMonth) {
}
