package com.xroig.finance.imports.infrastructure.bridge;

import com.xroig.finance.imports.domain.TransferWriter;
import com.xroig.finance.transfers.application.TransferQueryPort;
import com.xroig.finance.transfers.application.port.CreateTransfer;
import com.xroig.finance.transfers.application.port.CreateTransfer.TransferCommand;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Bridges {@link TransferWriter} to the transfers context: dedup reads come from the
 * read-side {@link TransferQueryPort} and writes go through the {@link CreateTransfer}
 * use case (so the aggregate invariants apply).
 */
@Component
class TransferWriterAdapter implements TransferWriter {

    private final TransferQueryPort queries;
    private final CreateTransfer createTransfer;

    TransferWriterAdapter(TransferQueryPort queries, CreateTransfer createTransfer) {
        this.queries = queries;
        this.createTransfer = createTransfer;
    }

    @Override
    public List<ExistingTransfer> existingBetween(LocalDate from, LocalDate to) {
        return queries.search(from, to, null).stream()
                .map(v -> new ExistingTransfer(v.fromAccount().id(), v.toAccount().id(),
                        v.date(), v.amount(), v.description()))
                .toList();
    }

    @Override
    public void create(NewTransfer transfer) {
        createTransfer.create(new TransferCommand(transfer.date(), transfer.amount(),
                transfer.description(), transfer.fromAccountId(), transfer.toAccountId()));
    }
}
