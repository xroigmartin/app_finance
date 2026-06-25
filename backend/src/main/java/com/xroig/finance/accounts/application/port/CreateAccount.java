package com.xroig.finance.accounts.application.port;

import com.xroig.finance.accounts.domain.Account;
import com.xroig.finance.shared.domain.Money;

/** Inbound port: create a new account. */
public interface CreateAccount {

    Account create(CreateAccountCommand command);

    /** Intent to create an account, in the domain's own terms. */
    record CreateAccountCommand(String name, String type, Money initialBalance) {
    }
}
