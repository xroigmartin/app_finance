package com.xroig.finance.categories.domain;

import com.xroig.finance.shared.domain.DomainId;

/** Typed identifier of the {@link Category} aggregate (also used to reference a parent category). */
public record CategoryId(Long value) implements DomainId {

    public CategoryId {
        if (value == null) {
            throw new IllegalArgumentException("CategoryId requiere un valor no nulo");
        }
    }
}
