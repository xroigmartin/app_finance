package com.xroig.finance.investments.domain;

import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link ImportRecord} value: one persisted attempt of a Flex
 * import (docs/plan/historial-imports.md H-imp.1). {@code of()} always builds
 * without an id (assigned on persist) and copies the errors/warnings lists
 * defensively.
 */
class ImportRecordTest {

    private static final PortfolioId PORTFOLIO = new PortfolioId(1L);
    private static final Instant IMPORTED_AT = Instant.parse("2026-07-26T10:15:30Z");
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO_DATE = LocalDate.of(2026, 6, 30);

    @Test
    void ofBuildsFromCountersWithoutId() {
        List<ImportRowIssue> errors = List.of(new ImportRowIssue("Trades", "T-1", "Instrumento desconocido"));
        List<String> warnings = List.of("2026-03-01: venta sin posición suficiente");

        ImportRecord record = ImportRecord.of(PORTFOLIO, IMPORTED_AT, "flex-2026-h1.csv",
                FROM_DATE, TO_DATE, 12, 3, errors, warnings);

        assertThat(record.id()).isNull();
        assertThat(record.portfolioId()).isEqualTo(PORTFOLIO);
        assertThat(record.importedAt()).isEqualTo(IMPORTED_AT);
        assertThat(record.fileName()).isEqualTo("flex-2026-h1.csv");
        assertThat(record.fromDate()).isEqualTo(FROM_DATE);
        assertThat(record.toDate()).isEqualTo(TO_DATE);
        assertThat(record.imported()).isEqualTo(12);
        assertThat(record.duplicated()).isEqualTo(3);
        assertThat(record.errors()).containsExactly(new ImportRowIssue("Trades", "T-1", "Instrumento desconocido"));
        assertThat(record.warnings()).containsExactly("2026-03-01: venta sin posición suficiente");
    }

    @Test
    void ofAcceptsNullFileNameAndFromDate() {
        ImportRecord record = ImportRecord.of(PORTFOLIO, IMPORTED_AT, null, null, TO_DATE, 0, 0,
                List.of(), List.of());

        assertThat(record.fileName()).isNull();
        assertThat(record.fromDate()).isNull();
        assertThat(record.toDate()).isEqualTo(TO_DATE);
    }

    @Test
    void requiresPortfolioImportedAtAndToDate() {
        assertThatThrownBy(() -> ImportRecord.of(null, IMPORTED_AT, "f.csv", FROM_DATE, TO_DATE, 0, 0,
                List.of(), List.of()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ImportRecord.of(PORTFOLIO, null, "f.csv", FROM_DATE, TO_DATE, 0, 0,
                List.of(), List.of()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ImportRecord.of(PORTFOLIO, IMPORTED_AT, "f.csv", FROM_DATE, null, 0, 0,
                List.of(), List.of()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void errorsAndWarningsAreDefensivelyCopied() {
        List<ImportRowIssue> mutableErrors = new ArrayList<>();
        mutableErrors.add(new ImportRowIssue("Trades", "T-1", "reason"));
        List<String> mutableWarnings = new ArrayList<>();
        mutableWarnings.add("warning");

        ImportRecord record = ImportRecord.of(PORTFOLIO, IMPORTED_AT, "f.csv", FROM_DATE, TO_DATE, 0, 0,
                mutableErrors, mutableWarnings);
        mutableErrors.clear();
        mutableWarnings.clear();

        assertThat(record.errors()).hasSize(1);
        assertThat(record.warnings()).hasSize(1);
    }
}
