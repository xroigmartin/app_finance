package com.xroig.finance.categorization.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Inbound web DTO for creating/editing a category rule. Bean validation rejects a blank
 * pattern or a missing category with 400; an unknown category is a domain validation
 * (→ 400 via {@code DomainExceptionHandler}). The pattern is trimmed by the aggregate.
 */
public record CategoryRuleRequest(@NotBlank String pattern, @NotNull Long categoryId) {
}
