package com.xroig.finance.transactions.domain;

import com.xroig.finance.shared.domain.DomainId;

/** Typed identifier of the {@link Transaction} aggregate (also used to reference the refunded movement). */
public record TransactionId(Long value) implements DomainId {

    public TransactionId {
        if (value == null) {
            throw new IllegalArgumentException("TransactionId requiere un valor no nulo");
        }
    }
}
