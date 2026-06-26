package com.xroig.finance.transfers.application.port;

import com.xroig.finance.transfers.application.TransferView;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Inbound port: create a transfer between two accounts. */
public interface CreateTransfer {

    TransferView create(TransferCommand command);

    /** Intent to create/update a transfer. */
    record TransferCommand(LocalDate date, BigDecimal amount, String description,
                           Long fromAccountId, Long toAccountId) {
    }
}
