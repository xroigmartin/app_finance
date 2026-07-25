package com.xroig.finance.shared.domain;

import java.util.List;

/**
 * Generic page of query results plus pagination metadata, shared by any CQRS read
 * port that lists rather than aggregates (investments operations, the combined
 * movements feed…). {@code totalPages} is derived from {@code totalElements}/{@code
 * size} (0 when there are no elements), never stored independently.
 */
public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public Page(List<T> content, int page, int size, long totalElements) {
        this(content, page, size, totalElements,
                size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size));
    }
}
