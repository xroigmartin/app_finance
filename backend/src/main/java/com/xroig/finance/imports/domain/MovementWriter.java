package com.xroig.finance.imports.domain;

import com.xroig.finance.shared.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Outbound port for persisting imported movements and reading back the ones already
 * stored in a date window (for deduplication). Bridged to the transactions context:
 * {@link #create} reuses its create use case so the aggregate invariants still apply.
 */
public interface MovementWriter {

    /** Movements already stored in {@code [from, to]} (inclusive), to dedup the file against. */
    List<ExistingMovement> existingBetween(LocalDate from, LocalDate to);

    void create(NewMovement movement);

    /** A movement resolved from a file row, ready to persist (amount already positive). */
    record NewMovement(LocalDate date, BigDecimal amount, String description,
                       TransactionType type, long accountId, long categoryId) {
    }

    /** The fields of an already-stored movement that take part in the dedup key. */
    record ExistingMovement(long accountId, LocalDate date, TransactionType type,
                            BigDecimal amount, String description) {
    }
}
