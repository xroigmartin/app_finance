package com.xroig.finance.categories.domain;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and reading {@link Category} aggregates on the
 * command side. Listings for the UI go through the read side
 * ({@code CategoryQueryPort}), not this port.
 */
public interface CategoryRepository {

    Optional<Category> findById(CategoryId id);

    Category save(Category category);

    void deleteById(CategoryId id);

    boolean existsByParentId(CategoryId parentId);

    /** The direct subcategories of a top-level category (used by the scope-reassignment guard). */
    List<Category> findChildren(CategoryId parentId);
}
