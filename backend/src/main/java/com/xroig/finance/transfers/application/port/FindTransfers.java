package com.xroig.finance.transfers.application.port;

import com.xroig.finance.transfers.application.TransferView;

import java.time.LocalDate;
import java.util.List;

/** Inbound port: read transfers (filtered search). */
public interface FindTransfers {

    List<TransferView> search(LocalDate from, LocalDate to, Long accountId);
}
