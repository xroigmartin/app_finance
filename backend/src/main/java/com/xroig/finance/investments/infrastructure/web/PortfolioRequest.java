package com.xroig.finance.investments.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Inbound web DTO for creating/renaming a portfolio. Only the name is validated at
 * the boundary: the base currency is required on creation by the domain itself
 * (ISO 4217, → 400) and ignored on update — it is immutable after creation (the
 * RN-7a snapshots anchor to it), so editing means renaming.
 */
public record PortfolioRequest(@NotBlank String name, String baseCurrency) {
}
