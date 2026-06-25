package com.xroig.finance.transactions.application.port;

import com.xroig.finance.transactions.application.TransactionView;
import com.xroig.finance.transactions.application.port.CreateTransaction.TransactionCommand;

/** Inbound port: edit a movement; fails if it does not exist. Reuses the create command shape. */
public interface UpdateTransaction {

    TransactionView update(long id, TransactionCommand command);
}
