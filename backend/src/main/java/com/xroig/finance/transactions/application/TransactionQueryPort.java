package com.xroig.finance.transactions.application;

import com.xroig.finance.transactions.domain.TransactionId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Read-side (CQRS) outbound port: resolves {@link TransactionView} read models directly
 * from persistence, without rebuilding aggregates.
 */
public interface TransactionQueryPort {

    /** Movements in {@code [from, to]} (inclusive), optionally filtered by account/category, newest first. */
    List<TransactionView> search(LocalDate from, LocalDate to, Long accountId, Long categoryId);

    /** The ten most recent movements. */
    List<TransactionView> recent();

    Optional<TransactionView> findById(TransactionId id);
}
