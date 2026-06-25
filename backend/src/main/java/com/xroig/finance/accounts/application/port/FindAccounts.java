package com.xroig.finance.accounts.application.port;

import com.xroig.finance.accounts.domain.Account;

import java.util.List;

/** Inbound port: list every account. */
public interface FindAccounts {

    List<Account> all();
}
