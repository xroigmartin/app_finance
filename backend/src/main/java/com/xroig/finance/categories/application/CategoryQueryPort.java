package com.xroig.finance.categories.application;

import com.xroig.finance.categories.domain.CategoryId;

import java.util.List;
import java.util.Optional;

/**
 * Read-side (CQRS) outbound port: resolves {@link CategoryView} read models directly
 * from persistence, without rebuilding aggregates. Implemented by an adapter in
 * {@code infrastructure}.
 */
public interface CategoryQueryPort {

    List<CategoryView> findAll();

    Optional<CategoryView> findById(CategoryId id);
}
