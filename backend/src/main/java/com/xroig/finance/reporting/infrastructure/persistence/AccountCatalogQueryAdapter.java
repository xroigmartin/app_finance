package com.xroig.finance.reporting.infrastructure.persistence;

import com.xroig.finance.reporting.application.AccountCatalogQuery;
import com.xroig.finance.repository.AccountRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/** Resolves {@link AccountCatalogQuery} from the accounts table (id/name/type/initial balance). */
@Component
class AccountCatalogQueryAdapter implements AccountCatalogQuery {

    private final AccountRepository accounts;

    AccountCatalogQueryAdapter(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public List<ReportAccount> all() {
        return accounts.findAll().stream()
                .map(a -> new ReportAccount(a.getId(), a.getName(), a.getType(), a.getInitialBalance()))
                .toList();
    }
}
