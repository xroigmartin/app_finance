package com.xroig.finance.accounts.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Inbound web DTO for creating/editing an account. Carries bean validation so a
 * malformed request is rejected with 400 at the boundary (mirroring the legacy
 * entity's {@code @NotBlank}/{@code @NotNull}); the domain re-checks the same
 * invariants independently.
 */
public record AccountRequest(
        @NotBlank String name,
        @NotBlank String type,
        @NotNull BigDecimal initialBalance) {
}
