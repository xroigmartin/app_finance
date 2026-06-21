package com.xroig.finance.controller;

import com.xroig.finance.dto.ImportDtos.ImportResult;
import com.xroig.finance.model.Account;
import com.xroig.finance.model.Transfer;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.TransferRepository;
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
@RequestMapping("/api/transfers")
public class TransferController {

    public record TransferRequest(@NotNull LocalDate date,
                                  @NotNull @Positive BigDecimal amount,
                                  String description,
                                  @NotNull Long fromAccountId,
                                  @NotNull Long toAccountId) {
    }

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final ImportService importService;

    public TransferController(TransferRepository transferRepository,
                              AccountRepository accountRepository,
                              ImportService importService) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.importService = importService;
    }

    @PostMapping("/import")
    public ImportResult importFile(@RequestParam("file") MultipartFile file) {
        return importService.importTransfers(file);
    }

    @GetMapping
    public List<Transfer> find(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long accountId) {
        LocalDate start = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate end = to != null ? to : LocalDate.of(2999, 12, 31);
        return transferRepository.search(start, end, accountId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Transfer create(@Valid @RequestBody TransferRequest request) {
        Transfer transfer = new Transfer();
        apply(transfer, request);
        return transferRepository.save(transfer);
    }

    @PutMapping("/{id}")
    public Transfer update(@PathVariable Long id, @Valid @RequestBody TransferRequest request) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transferencia no encontrada"));
        apply(transfer, request);
        return transferRepository.save(transfer);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        transferRepository.deleteById(id);
    }

    private void apply(Transfer transfer, TransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cuenta de origen y la de destino deben ser distintas");
        }
        transfer.setDate(request.date());
        transfer.setAmount(request.amount());
        transfer.setDescription(request.description());
        transfer.setFromAccount(resolveAccount(request.fromAccountId()));
        transfer.setToAccount(resolveAccount(request.toAccountId()));
    }

    private Account resolveAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cuenta no válida"));
    }
}
