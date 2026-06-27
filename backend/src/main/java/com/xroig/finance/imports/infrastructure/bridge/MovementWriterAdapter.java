package com.xroig.finance.imports.infrastructure.bridge;

import com.xroig.finance.imports.domain.MovementWriter;
import com.xroig.finance.transactions.application.TransactionQueryPort;
import com.xroig.finance.transactions.application.port.CreateTransaction;
import com.xroig.finance.transactions.application.port.CreateTransaction.TransactionCommand;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Bridges {@link MovementWriter} to the transactions context: dedup reads come from the
 * read-side {@link TransactionQueryPort} and writes go through the {@link
 * CreateTransaction} use case (so the aggregate invariants apply).
 */
@Component
class MovementWriterAdapter implements MovementWriter {

    private final TransactionQueryPort queries;
    private final CreateTransaction createTransaction;

    MovementWriterAdapter(TransactionQueryPort queries, CreateTransaction createTransaction) {
        this.queries = queries;
        this.createTransaction = createTransaction;
    }

    @Override
    public List<ExistingMovement> existingBetween(LocalDate from, LocalDate to) {
        return queries.search(from, to, null, null).stream()
                .map(v -> new ExistingMovement(v.account().id(), v.date(), v.type(), v.amount(), v.description()))
                .toList();
    }

    @Override
    public void create(NewMovement movement) {
        createTransaction.create(new TransactionCommand(movement.date(), movement.amount(),
                movement.description(), movement.type(), movement.accountId(), movement.categoryId(), null));
    }
}
