package com.xroig.finance.categories.infrastructure.web;

import com.xroig.finance.shared.domain.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Inbound web DTO for creating/editing a category. Mirrors the legacy entity body,
 * including the optional nested {@code parent}/{@code account} that carry only an
 * {@code id}. Bean validation rejects a blank name or missing type with 400.
 */
public record CategoryRequest(
        @NotBlank String name,
        @NotNull TransactionType type,
        String color,
        Ref parent,
        Ref account) {

    /** A nested reference that carries only the target id (e.g. {@code {"id": 5}}). */
    public record Ref(Long id) {
    }

    public Long parentId() {
        return parent == null ? null : parent.id();
    }

    public Long accountId() {
        return account == null ? null : account.id();
    }
}
