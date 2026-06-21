package com.xroig.finance.dto;

import java.util.List;

public final class ImportDtos {

    private ImportDtos() {
    }

    public record RowError(int row, String message) {
    }

    public record ImportResult(int imported, int duplicated, List<RowError> errors) {
    }
}
