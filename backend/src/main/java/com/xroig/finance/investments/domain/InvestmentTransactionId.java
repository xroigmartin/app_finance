package com.xroig.finance.investments.domain;

import com.xroig.finance.shared.domain.DomainId;

/**
 * Typed identifier of the {@code InvestmentTransaction} aggregate. Replaces the raw
 * {@code Long} key at the domain boundary.
 */
public record InvestmentTransactionId(Long value) implements DomainId {

    public InvestmentTransactionId {
        if (value == null) {
            throw new IllegalArgumentException("InvestmentTransactionId requiere un valor no nulo");
        }
    }
}
