package com.xroig.finance.accounts.application.port;

/** Inbound port: delete an account, guarded against linked movements/transfers. */
public interface DeleteAccount {

    void delete(long id);
}
