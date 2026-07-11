package com.xroig.finance.investments.domain;

import com.xroig.finance.shared.domain.DomainId;

/**
 * Typed identifier of the {@code Security} aggregate. Replaces the raw {@code Long}
 * key at the domain boundary so transactions and quotes can reference an instrument
 * by identity without holding the object.
 */
public record SecurityId(Long value) implements DomainId {

    public SecurityId {
        if (value == null) {
            throw new IllegalArgumentException("SecurityId requiere un valor no nulo");
        }
    }
}
