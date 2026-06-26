package com.xroig.finance.transfers.domain;

import com.xroig.finance.shared.domain.DomainId;

/** Typed identifier of the {@link Transfer} aggregate. */
public record TransferId(Long value) implements DomainId {

    public TransferId {
        if (value == null) {
            throw new IllegalArgumentException("TransferId requiere un valor no nulo");
        }
    }
}
