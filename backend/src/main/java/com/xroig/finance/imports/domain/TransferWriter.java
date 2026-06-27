package com.xroig.finance.imports.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Outbound port for persisting imported transfers and reading back the ones already
 * stored in a date window (for deduplication). Bridged to the transfers context:
 * {@link #create} reuses its create use case so the aggregate invariants still apply.
 */
public interface TransferWriter {

    /** Transfers already stored in {@code [from, to]} (inclusive), to dedup the file against. */
    List<ExistingTransfer> existingBetween(LocalDate from, LocalDate to);

    void create(NewTransfer transfer);

    /** A transfer resolved from a file row, ready to persist (amount already positive). */
    record NewTransfer(LocalDate date, BigDecimal amount, String description,
                       long fromAccountId, long toAccountId) {
    }

    /** The fields of an already-stored transfer that take part in the dedup key. */
    record ExistingTransfer(long fromAccountId, long toAccountId, LocalDate date,
                            BigDecimal amount, String description) {
    }
}
