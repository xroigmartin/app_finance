package com.xroig.finance.controller;

import com.xroig.finance.dto.ImportDtos.ImportResult;
import com.xroig.finance.model.Transaction;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.TransactionRepository;
import com.xroig.finance.service.ImportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    public record TransactionRequest(@NotNull LocalDate date,
                                     @NotNull @Positive BigDecimal amount,
                                     String description,
                                     @NotNull TransactionType type,
                                     @NotNull Long accountId,
                                     @NotNull Long categoryId,
                                     /** When set, this movement is a refund of that expense. */
                                     Long refundOfId) {
    }

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final ImportService importService;

    public TransactionController(TransactionRepository transactionRepository,
                                 AccountRepository accountRepository,
                                 CategoryRepository categoryRepository,
                                 ImportService importService) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.importService = importService;
    }

    @PostMapping("/import")
    public ImportResult importFile(@RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) Long accountId) {
        return importService.importTransactions(file, accountId);
    }

    @GetMapping
    public List<Transaction> find(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long categoryId) {
        LocalDate start = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate end = to != null ? to : LocalDate.of(2999, 12, 31);
        return transactionRepository.search(start, end, accountId, categoryId);
    }

    @GetMapping("/recent")
    public List<Transaction> recent() {
        return transactionRepository.findTop10ByOrderByDateDescIdDesc();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Transaction create(@Valid @RequestBody TransactionRequest request) {
        Transaction transaction = new Transaction();
        apply(transaction, request);
        return transactionRepository.save(transaction);
    }

    @PutMapping("/{id}")
    public Transaction update(@PathVariable Long id, @Valid @RequestBody TransactionRequest request) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado"));
        apply(transaction, request);
        return transactionRepository.save(transaction);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        transactionRepository.deleteById(id);
    }

    private void apply(Transaction transaction, TransactionRequest request) {
        transaction.setDate(request.date());
        transaction.setAmount(request.amount());
        transaction.setDescription(request.description());

        if (request.refundOfId() != null) {
            applyRefund(transaction, request);
            return;
        }

        transaction.setRefundOf(null);
        transaction.setType(request.type());
        var account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cuenta no válida"));
        var category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoría no válida"));
        if (category.getAccount() != null && !category.getAccount().getId().equals(account.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La categoría pertenece a otra cuenta");
        }
        transaction.setAccount(account);
        transaction.setCategory(category);
    }

    /**
     * A refund inherits the original expense's account, category and type; only
     * its date, amount and description come from the request. The refunded total
     * cannot exceed the original amount.
     */
    private void applyRefund(Transaction transaction, TransactionRequest request) {
        Transaction original = transactionRepository.findById(request.refundOfId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El gasto que se quiere devolver no existe"));
        if (original.getId().equals(transaction.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un movimiento no puede ser su propia devolución");
        }
        if (original.getRefundOf() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se puede registrar una devolución de otra devolución");
        }
        if (original.getType() != TransactionType.EXPENSE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se pueden registrar devoluciones de gastos");
        }
        BigDecimal alreadyRefunded = transactionRepository.sumRefundedAmount(original.getId(), transaction.getId());
        BigDecimal pending = original.getAmount().subtract(alreadyRefunded);
        if (request.amount().compareTo(pending) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La devolución supera el importe pendiente del gasto (pendiente: " + pending + ")");
        }
        transaction.setType(original.getType());
        transaction.setAccount(original.getAccount());
        transaction.setCategory(original.getCategory());
        transaction.setRefundOf(original);
    }
}
