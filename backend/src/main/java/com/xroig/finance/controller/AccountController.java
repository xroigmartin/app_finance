package com.xroig.finance.controller;

import com.xroig.finance.model.Account;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.TransactionRepository;
import com.xroig.finance.repository.TransferRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransferRepository transferRepository;

    public AccountController(AccountRepository accountRepository,
                             TransactionRepository transactionRepository,
                             TransferRepository transferRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transferRepository = transferRepository;
    }

    @GetMapping
    public List<Account> findAll() {
        return accountRepository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Account create(@Valid @RequestBody Account account) {
        account.setId(null);
        return accountRepository.save(account);
    }

    @PutMapping("/{id}")
    public Account update(@PathVariable Long id, @Valid @RequestBody Account account) {
        Account existing = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
        existing.setName(account.getName());
        existing.setType(account.getType());
        existing.setInitialBalance(account.getInitialBalance());
        return accountRepository.save(existing);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (transactionRepository.existsByAccountId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La cuenta tiene movimientos asociados y no se puede eliminar");
        }
        if (transferRepository.existsByFromAccountIdOrToAccountId(id, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La cuenta tiene transferencias asociadas y no se puede eliminar");
        }
        accountRepository.deleteById(id);
    }
}
