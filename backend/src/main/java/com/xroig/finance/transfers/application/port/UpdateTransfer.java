package com.xroig.finance.transfers.application.port;

import com.xroig.finance.transfers.application.TransferView;
import com.xroig.finance.transfers.application.port.CreateTransfer.TransferCommand;

/** Inbound port: edit a transfer; fails if it does not exist. Reuses the create command shape. */
public interface UpdateTransfer {

    TransferView update(long id, TransferCommand command);
}
