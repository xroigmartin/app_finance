package com.xroig.finance.transactions.domain;

import com.xroig.finance.shared.domain.Money;

import java.util.Optional;

/**
 * Outbound port for persisting {@link Transaction} aggregates on the command side.
 * Listings for the UI go through the read side ({@code TransactionQueryPort}).
 */
public interface TransactionRepository {

    Optional<Transaction> findById(TransactionId id);

    Transaction save(Transaction transaction);

    void deleteById(TransactionId id);

    /**
     * Total already refunded of an original movement, optionally excluding one refund
     * (used when editing that refund, so its own amount is not counted).
     */
    Money refundedAmountFor(TransactionId originalId, TransactionId excludeId);
}
