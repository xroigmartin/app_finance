package com.xroig.finance.reporting.application;

import com.xroig.finance.shared.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-side (CQRS) outbound port: net aggregations over movements (refunds netted),
 * resolved directly from persistence. Implemented by an adapter in {@code infrastructure}.
 */
public interface MovementAggregateQuery {

    /** Net sum of {@code type} in {@code [from, to]}, scoped to an account when given. */
    BigDecimal sumByType(TransactionType type, LocalDate from, LocalDate to, Long accountId);

    /** Net movement total of an account up to (and including) {@code until}. */
    BigDecimal netByAccountUntil(Long accountId, LocalDate until);

    /** Net spending of a category and its subcategories (one-level roll-up) in a period, scoped to an account. */
    BigDecimal spentByCategoryTree(Long categoryId, LocalDate from, LocalDate to, Long accountId);

    /** Net amount per top-level category (subcategories rolled up), newest-largest first. */
    List<CategoryShare> shareByCategory(TransactionType type, LocalDate from, LocalDate to, Long accountId);

    /** A category's net amount with its display color, as returned by the roll-up aggregation. */
    record CategoryShare(String category, String color, BigDecimal amount) {
    }
}
