package com.xroig.finance.accounts.application.port;

import com.xroig.finance.accounts.domain.Account;
import com.xroig.finance.shared.domain.Money;

/** Inbound port: edit an existing account; fails if it does not exist. */
public interface UpdateAccount {

    Account update(long id, UpdateAccountCommand command);

    /** Intent to change an account's editable fields. */
    record UpdateAccountCommand(String name, String type, Money initialBalance) {
    }
}
