package com.xroig.finance.transfers.application;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
import com.xroig.finance.transfers.application.port.CreateTransfer;
import com.xroig.finance.transfers.application.port.DeleteTransfer;
import com.xroig.finance.transfers.application.port.FindTransfers;
import com.xroig.finance.transfers.application.port.UpdateTransfer;
import com.xroig.finance.transfers.domain.AccountExistence;
import com.xroig.finance.transfers.domain.Transfer;
import com.xroig.finance.transfers.domain.TransferId;
import com.xroig.finance.transfers.domain.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Application service for the transfers context. Orchestrates the {@link Transfer}
 * aggregate and the outbound ports, preserving the legacy {@code apply} order: the
 * distinct-ends invariant (in the aggregate) fires before the account-existence checks.
 */
@Service
@Transactional
public class TransferService implements FindTransfers, CreateTransfer, UpdateTransfer, DeleteTransfer {

    private final TransferRepository transfers;
    private final AccountExistence accounts;
    private final TransferQueryPort queries;

    public TransferService(TransferRepository transfers, AccountExistence accounts, TransferQueryPort queries) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.queries = queries;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransferView> search(LocalDate from, LocalDate to, Long accountId) {
        return queries.search(from, to, accountId);
    }

    @Override
    public TransferView create(TransferCommand command) {
        AccountId from = new AccountId(command.fromAccountId());
        AccountId to = new AccountId(command.toAccountId());
        // The aggregate enforces distinct ends first (as the legacy did), then existence.
        Transfer transfer = Transfer.create(command.date(), Money.of(command.amount()), command.description(), from, to);
        requireExists(from);
        requireExists(to);
        return view(transfers.save(transfer).id());
    }

    @Override
    public TransferView update(long id, TransferCommand command) {
        Transfer existing = transfers.findById(new TransferId(id))
                .orElseThrow(() -> new NotFoundException("Transferencia no encontrada"));
        AccountId from = new AccountId(command.fromAccountId());
        AccountId to = new AccountId(command.toAccountId());
        existing.reassign(command.date(), Money.of(command.amount()), command.description(), from, to);
        requireExists(from);
        requireExists(to);
        return view(transfers.save(existing).id());
    }

    @Override
    public void delete(long id) {
        transfers.deleteById(new TransferId(id));
    }

    private void requireExists(AccountId accountId) {
        if (!accounts.exists(accountId)) {
            throw new ValidationException("Cuenta no válida");
        }
    }

    private TransferView view(TransferId id) {
        return queries.findById(id).orElseThrow(
                () -> new IllegalStateException("La transferencia recién guardada no se pudo leer: " + id.value()));
    }
}
