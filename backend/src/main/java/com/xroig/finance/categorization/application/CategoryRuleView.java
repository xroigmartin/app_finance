package com.xroig.finance.categorization.application;

import com.xroig.finance.categories.application.CategoryView;

/**
 * Read model (CQRS) for a category rule as the API exposes it: the flat fields plus the
 * nested target {@code category} (reusing the categories context's {@link CategoryView}),
 * reproducing the JSON the legacy entity serialized. The read adapter assembles it from
 * the persistence graph; it is never the aggregate.
 */
public record CategoryRuleView(Long id, String pattern, CategoryView category) {
}
