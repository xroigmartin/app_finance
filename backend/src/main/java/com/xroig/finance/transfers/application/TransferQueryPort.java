package com.xroig.finance.transfers.application;

import com.xroig.finance.transfers.domain.TransferId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Read-side (CQRS) outbound port: resolves {@link TransferView} read models from persistence. */
public interface TransferQueryPort {

    /** Transfers in {@code [from, to]} touching {@code accountId} (as source or destination) when given, newest first. */
    List<TransferView> search(LocalDate from, LocalDate to, Long accountId);

    Optional<TransferView> findById(TransferId id);
}
