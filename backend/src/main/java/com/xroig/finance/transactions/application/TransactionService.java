package com.xroig.finance.transactions.application;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
import com.xroig.finance.transactions.application.port.CreateTransaction;
import com.xroig.finance.transactions.application.port.DeleteTransaction;
import com.xroig.finance.transactions.application.port.FindTransactions;
import com.xroig.finance.transactions.application.port.UpdateTransaction;
import com.xroig.finance.transactions.domain.AccountExistence;
import com.xroig.finance.transactions.domain.CategoryCatalog;
import com.xroig.finance.transactions.domain.CategoryCatalog.CategoryDescriptor;
import com.xroig.finance.transactions.domain.Transaction;
import com.xroig.finance.transactions.domain.TransactionId;
import com.xroig.finance.transactions.domain.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Application service for the transactions context. Orchestrates the {@link Transaction}
 * aggregate and the outbound ports, preserving the legacy {@code apply}/{@code
 * applyRefund} order so a given input yields the same status. The refund invariants
 * (not a refund of a refund, only of expenses, within the pending amount) live in the
 * aggregate; resolving the original/account/category and the "self refund" check live
 * here.
 */
@Service
@Transactional
public class TransactionService implements FindTransactions, CreateTransaction, UpdateTransaction, DeleteTransaction {

    private final TransactionRepository transactions;
    private final AccountExistence accounts;
    private final CategoryCatalog categories;
    private final TransactionQueryPort queries;

    public TransactionService(TransactionRepository transactions, AccountExistence accounts,
                              CategoryCatalog categories, TransactionQueryPort queries) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.categories = categories;
        this.queries = queries;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionView> search(LocalDate from, LocalDate to, Long accountId, Long categoryId) {
        return queries.search(from, to, accountId, categoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionView> recent() {
        return queries.recent();
    }

    @Override
    public TransactionView create(TransactionCommand command) {
        Transaction transaction = command.refundOfId() != null
                ? buildRefund(null, command)
                : buildNormal(command);
        return view(transactions.save(transaction).id());
    }

    @Override
    public TransactionView update(long id, TransactionCommand command) {
        TransactionId transactionId = new TransactionId(id);
        Transaction existing = transactions.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Movimiento no encontrado"));
        if (command.refundOfId() != null) {
            Refund refund = resolveRefund(transactionId, command);
            existing.changeToRefund(command.date(), Money.of(command.amount()), command.description(),
                    refund.original(), refund.alreadyRefunded());
        } else {
            Assignment assignment = resolveAssignment(command);
            existing.changeToNormal(command.date(), Money.of(command.amount()), command.description(),
                    command.type(), assignment.accountId(), assignment.categoryId());
        }
        return view(transactions.save(existing).id());
    }

    @Override
    public void delete(long id) {
        transactions.deleteById(new TransactionId(id));
    }

    // ---- create helpers (no current id) ----

    private Transaction buildNormal(TransactionCommand command) {
        Assignment assignment = resolveAssignment(command);
        return Transaction.record(command.date(), Money.of(command.amount()), command.description(),
                command.type(), assignment.accountId(), assignment.categoryId());
    }

    private Transaction buildRefund(TransactionId currentId, TransactionCommand command) {
        Refund refund = resolveRefund(currentId, command);
        return Transaction.refundOf(command.date(), Money.of(command.amount()), command.description(),
                refund.original(), refund.alreadyRefunded());
    }

    /** Resolves and validates the account/category a normal movement points at. */
    private Assignment resolveAssignment(TransactionCommand command) {
        AccountId accountId = new AccountId(command.accountId());
        if (!accounts.exists(accountId)) {
            throw new ValidationException("Cuenta no válida");
        }
        CategoryDescriptor category = categories.find(new CategoryId(command.categoryId()))
                .orElseThrow(() -> new ValidationException("Categoría no válida"));
        if (!category.isGlobal() && !category.accountId().equals(accountId)) {
            throw new ValidationException("La categoría pertenece a otra cuenta");
        }
        return new Assignment(accountId, category.id());
    }

    /** Loads the original expense being refunded and the amount already refunded of it. */
    private Refund resolveRefund(TransactionId currentId, TransactionCommand command) {
        TransactionId originalId = new TransactionId(command.refundOfId());
        Transaction original = transactions.findById(originalId)
                .orElseThrow(() -> new ValidationException("El gasto que se quiere devolver no existe"));
        if (originalId.equals(currentId)) {
            throw new ValidationException("Un movimiento no puede ser su propia devolución");
        }
        Money alreadyRefunded = transactions.refundedAmountFor(originalId, currentId);
        return new Refund(original, alreadyRefunded);
    }

    private TransactionView view(TransactionId id) {
        return queries.findById(id).orElseThrow(
                () -> new IllegalStateException("El movimiento recién guardado no se pudo leer: " + id.value()));
    }

    private record Assignment(AccountId accountId, CategoryId categoryId) {
    }

    private record Refund(Transaction original, Money alreadyRefunded) {
    }
}
