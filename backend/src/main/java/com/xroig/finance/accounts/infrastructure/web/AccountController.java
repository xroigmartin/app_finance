package com.xroig.finance.accounts.infrastructure.web;

import com.xroig.finance.accounts.application.port.CreateAccount;
import com.xroig.finance.accounts.application.port.CreateAccount.CreateAccountCommand;
import com.xroig.finance.accounts.application.port.DeleteAccount;
import com.xroig.finance.accounts.application.port.FindAccounts;
import com.xroig.finance.accounts.application.port.UpdateAccount;
import com.xroig.finance.accounts.application.port.UpdateAccount.UpdateAccountCommand;
import com.xroig.finance.accounts.domain.Account;
import com.xroig.finance.shared.domain.Money;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Inbound web adapter for the accounts context. Thin by design: it (de)serializes
 * DTOs and delegates to the inbound ports — it never touches repositories. Domain
 * failures (not found → 404, deletion guard → 409) are translated to HTTP by
 * {@code shared.web.DomainExceptionHandler}.
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final FindAccounts findAccounts;
    private final CreateAccount createAccount;
    private final UpdateAccount updateAccount;
    private final DeleteAccount deleteAccount;

    public AccountController(FindAccounts findAccounts, CreateAccount createAccount,
                            UpdateAccount updateAccount, DeleteAccount deleteAccount) {
        this.findAccounts = findAccounts;
        this.createAccount = createAccount;
        this.updateAccount = updateAccount;
        this.deleteAccount = deleteAccount;
    }

    @GetMapping
    public List<AccountResponse> findAll() {
        return findAccounts.all().stream().map(AccountResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody AccountRequest request) {
        Account created = createAccount.create(new CreateAccountCommand(
                request.name(), request.type(), Money.of(request.initialBalance())));
        return AccountResponse.from(created);
    }

    @PutMapping("/{id}")
    public AccountResponse update(@PathVariable Long id, @Valid @RequestBody AccountRequest request) {
        Account updated = updateAccount.update(id, new UpdateAccountCommand(
                request.name(), request.type(), Money.of(request.initialBalance())));
        return AccountResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deleteAccount.delete(id);
    }
}
