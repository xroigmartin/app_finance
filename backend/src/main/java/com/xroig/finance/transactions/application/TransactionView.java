package com.xroig.finance.transactions.application;

import com.xroig.finance.shared.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read model (CQRS) for a movement as the API exposes it: the flat fields plus the
 * nested {@code account} and {@code category} and, when it is a refund, a {@code
 * refundOf} reference. The read adapter assembles it from the persistence graph.
 *
 * <p>Faithful to the legacy JSON for every field the UI consumes; the nested
 * {@code category} is flattened to {id,name,type,color} (its own account/parent
 * were never read from a movement's category) and {@code refundOf} carries only the
 * id (the UI looks the original up in the list).
 */
public record TransactionView(Long id, LocalDate date, BigDecimal amount, String description,
                              TransactionType type, AccountRef account, CategoryRef category,
                              RefundRef refundOf) {

    public record AccountRef(Long id, String name, String type, BigDecimal initialBalance) {
    }

    public record CategoryRef(Long id, String name, TransactionType type, String color) {
    }

    public record RefundRef(Long id) {
    }
}
