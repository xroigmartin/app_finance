package com.xroig.finance.reporting.application;

import com.xroig.finance.transactions.application.TransactionView;
import com.xroig.finance.transfers.application.TransferView;

import java.time.LocalDate;

/**
 * One row of the combined "Movimientos" feed: a transaction or a transfer, still
 * fully shaped as its own read model. {@code source} is {@code "tx"}/{@code "tr"}
 * (matching the frontend's existing discriminator); exactly one of {@code tx}/
 * {@code tr} is non-null depending on it.
 */
public record MovementView(String source, LocalDate date, Long id, TransactionView tx, TransferView tr) {
}
