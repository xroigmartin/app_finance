package com.xroig.finance.transactions.infrastructure.web;

import com.xroig.finance.imports.application.ImportResult;
import com.xroig.finance.imports.application.port.ImportTransactions;
import com.xroig.finance.transactions.application.TransactionView;
import com.xroig.finance.transactions.application.port.CreateTransaction;
import com.xroig.finance.transactions.application.port.CreateTransaction.TransactionCommand;
import com.xroig.finance.transactions.application.port.DeleteTransaction;
import com.xroig.finance.transactions.application.port.FindTransactions;
import com.xroig.finance.transactions.application.port.UpdateTransaction;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * Inbound web adapter for the transactions context. Thin: it (de)serializes DTOs and
 * delegates to the inbound ports, returning the {@link TransactionView} read model.
 *
 * <p>The {@code /import} endpoint forwards the upload to the imports context's {@link
 * ImportTransactions} use case.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final FindTransactions findTransactions;
    private final CreateTransaction createTransaction;
    private final UpdateTransaction updateTransaction;
    private final DeleteTransaction deleteTransaction;
    private final ImportTransactions importTransactions;

    public TransactionController(FindTransactions findTransactions, CreateTransaction createTransaction,
                                UpdateTransaction updateTransaction, DeleteTransaction deleteTransaction,
                                ImportTransactions importTransactions) {
        this.findTransactions = findTransactions;
        this.createTransaction = createTransaction;
        this.updateTransaction = updateTransaction;
        this.deleteTransaction = deleteTransaction;
        this.importTransactions = importTransactions;
    }

    @PostMapping("/import")
    public ImportResult importFile(@RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) Long accountId) {
        return importTransactions.importTransactions(file, accountId);
    }

    @GetMapping
    public List<TransactionView> find(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long categoryId) {
        LocalDate start = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate end = to != null ? to : LocalDate.of(2999, 12, 31);
        return findTransactions.search(start, end, accountId, categoryId);
    }

    @GetMapping("/recent")
    public List<TransactionView> recent() {
        return findTransactions.recent();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionView create(@Valid @RequestBody TransactionRequest request) {
        return createTransaction.create(toCommand(request));
    }

    @PutMapping("/{id}")
    public TransactionView update(@PathVariable Long id, @Valid @RequestBody TransactionRequest request) {
        return updateTransaction.update(id, toCommand(request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deleteTransaction.delete(id);
    }

    private static TransactionCommand toCommand(TransactionRequest r) {
        return new TransactionCommand(r.date(), r.amount(), r.description(), r.type(),
                r.accountId(), r.categoryId(), r.refundOfId());
    }
}
