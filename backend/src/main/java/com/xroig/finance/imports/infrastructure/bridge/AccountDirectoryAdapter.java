package com.xroig.finance.imports.infrastructure.bridge;

import com.xroig.finance.accounts.application.port.FindAccounts;
import com.xroig.finance.imports.domain.AccountDirectory;
import org.springframework.stereotype.Component;

import java.util.List;

/** Bridges {@link AccountDirectory} to the accounts context's {@link FindAccounts} use case. */
@Component
class AccountDirectoryAdapter implements AccountDirectory {

    private final FindAccounts accounts;

    AccountDirectoryAdapter(FindAccounts accounts) {
        this.accounts = accounts;
    }

    @Override
    public List<ImportAccount> all() {
        return accounts.all().stream()
                .map(a -> new ImportAccount(a.id().value(), a.name()))
                .toList();
    }
}
