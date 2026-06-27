package com.xroig.finance.imports.application;

import java.util.List;

/**
 * Outcome of an import: how many rows were imported, how many were skipped as
 * duplicates and, per failing row, its (header-offset) number and the error message.
 * Serialized as-is by the web adapter — faithful to the legacy JSON.
 */
public record ImportResult(int imported, int duplicated, List<RowError> errors) {

    public record RowError(int row, String message) {
    }
}
