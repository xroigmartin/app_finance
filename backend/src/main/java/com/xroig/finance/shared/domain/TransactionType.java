package com.xroig.finance.shared.domain;

/**
 * The kind of a movement: money in or money out. A shared-kernel value used across
 * several bounded contexts (transactions, categories, budgets, categorization,
 * imports, reporting), so it lives in {@code shared/domain} rather than in any one
 * context — a pure enum, free of framework or persistence concerns.
 */
public enum TransactionType {
    INCOME,
    EXPENSE
}
