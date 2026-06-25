package com.xroig.finance.categories.application;

import com.xroig.finance.model.TransactionType;

import java.math.BigDecimal;

/**
 * Read model (CQRS) for a category as the API exposes it: the flat fields plus the
 * nested owning {@code account} (or null when global) and nested {@code parent}
 * (or null when top-level), reproducing the JSON the legacy entity serialized. The
 * read adapter assembles it from the persistence associations; it is never the
 * aggregate.
 */
public record CategoryView(Long id, String name, TransactionType type, String color,
                           AccountRef account, CategoryView parent) {

    /** The owning account of a category, as nested in the read model. */
    public record AccountRef(Long id, String name, String type, BigDecimal initialBalance) {
    }
}
