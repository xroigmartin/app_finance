package com.xroig.finance.transactions.application.port;

/** Inbound port: delete a movement (movements have no deletion guard). */
public interface DeleteTransaction {

    void delete(long id);
}
