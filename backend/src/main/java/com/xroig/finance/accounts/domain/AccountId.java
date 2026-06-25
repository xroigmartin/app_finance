package com.xroig.finance.accounts.domain;

import com.xroig.finance.shared.domain.DomainId;

/**
 * Typed identifier of the {@link Account} aggregate. Replaces the raw {@code Long}
 * key at the domain boundary so other aggregates can reference an account by
 * identity without holding the object.
 */
public record AccountId(Long value) implements DomainId {

    public AccountId {
        if (value == null) {
            throw new IllegalArgumentException("AccountId requiere un valor no nulo");
        }
    }
}
