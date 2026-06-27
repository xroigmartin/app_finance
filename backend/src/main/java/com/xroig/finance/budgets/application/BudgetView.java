package com.xroig.finance.budgets.application;

import com.xroig.finance.shared.domain.TransactionType;

import java.math.BigDecimal;

/**
 * Read model (CQRS) for a single budget as the API exposes it: the flat fields plus
 * the nested {@code account}/{@code category}. Assembled by the read adapter from the
 * persistence graph.
 */
public record BudgetView(Long id, AccountRef account, CategoryRef category,
                         int year, int month, BigDecimal amount) {

    public record AccountRef(Long id, String name, String type, BigDecimal initialBalance) {
    }

    public record CategoryRef(Long id, String name, TransactionType type, String color) {
    }
}
