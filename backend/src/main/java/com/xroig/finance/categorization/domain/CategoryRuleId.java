package com.xroig.finance.categorization.domain;

import com.xroig.finance.shared.domain.DomainId;

/** Typed identifier of the {@link CategoryRule} aggregate. */
public record CategoryRuleId(Long value) implements DomainId {

    public CategoryRuleId {
        if (value == null) {
            throw new IllegalArgumentException("CategoryRuleId requiere un valor no nulo");
        }
    }
}
