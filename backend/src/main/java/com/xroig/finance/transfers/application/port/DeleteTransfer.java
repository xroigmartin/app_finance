package com.xroig.finance.transfers.application.port;

/** Inbound port: delete a transfer (transfers have no deletion guard). */
public interface DeleteTransfer {

    void delete(long id);
}
