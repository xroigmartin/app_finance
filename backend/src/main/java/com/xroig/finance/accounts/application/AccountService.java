package com.xroig.finance.accounts.application;

import com.xroig.finance.accounts.application.port.CreateAccount;
import com.xroig.finance.accounts.application.port.DeleteAccount;
import com.xroig.finance.accounts.application.port.FindAccounts;
import com.xroig.finance.accounts.application.port.UpdateAccount;
import com.xroig.finance.accounts.domain.Account;
import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.accounts.domain.AccountRepository;
import com.xroig.finance.accounts.domain.AccountUsage;
import com.xroig.finance.shared.domain.ConflictException;
import com.xroig.finance.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application service for the accounts context: implements every use case by
 * orchestrating the domain aggregate and the outbound ports. Holds no business
 * rules of its own — invariants live in {@link Account}; here we only resolve
 * identity, apply the cross-aggregate deletion guard (via {@link AccountUsage})
 * and translate "not found" into the domain's {@link NotFoundException}.
 */
@Service
@Transactional
public class AccountService implements FindAccounts, CreateAccount, UpdateAccount, DeleteAccount {

    private final AccountRepository accounts;
    private final AccountUsage usage;

    public AccountService(AccountRepository accounts, AccountUsage usage) {
        this.accounts = accounts;
        this.usage = usage;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Account> all() {
        return accounts.findAll();
    }

    @Override
    public Account create(CreateAccountCommand command) {
        return accounts.save(Account.create(command.name(), command.type(), command.initialBalance()));
    }

    @Override
    public Account update(long id, UpdateAccountCommand command) {
        Account account = accounts.findById(new AccountId(id))
                .orElseThrow(() -> new NotFoundException("Cuenta no encontrada"));
        account.update(command.name(), command.type(), command.initialBalance());
        return accounts.save(account);
    }

    @Override
    public void delete(long id) {
        AccountId accountId = new AccountId(id);
        if (usage.hasMovements(accountId)) {
            throw new ConflictException("La cuenta tiene movimientos asociados y no se puede eliminar");
        }
        if (usage.hasTransfers(accountId)) {
            throw new ConflictException("La cuenta tiene transferencias asociadas y no se puede eliminar");
        }
        accounts.deleteById(accountId);
    }
}
