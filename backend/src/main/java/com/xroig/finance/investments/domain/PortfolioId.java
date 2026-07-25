package com.xroig.finance.investments.domain;

import com.xroig.finance.shared.domain.DomainId;

/**
 * Typed identifier of the {@code Portfolio} aggregate. Replaces the raw {@code Long}
 * key at the domain boundary so other aggregates can reference a portfolio by
 * identity without holding the object.
 */
public record PortfolioId(Long value) implements DomainId {

    public PortfolioId {
        if (value == null) {
            throw new IllegalArgumentException("PortfolioId requiere un valor no nulo");
        }
    }
}
