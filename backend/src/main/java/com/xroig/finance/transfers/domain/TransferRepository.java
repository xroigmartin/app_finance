package com.xroig.finance.transfers.domain;

import java.util.Optional;

/**
 * Outbound port for persisting {@link Transfer} aggregates on the command side.
 * Listings for the UI go through the read side ({@code TransferQueryPort}).
 */
public interface TransferRepository {

    Optional<Transfer> findById(TransferId id);

    Transfer save(Transfer transfer);

    void deleteById(TransferId id);
}
