package com.xroig.finance.investments.domain;

import com.xroig.finance.shared.domain.ValidationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One persisted attempt of a Flex import (docs/plan/historial-imports.md,
 * precursor of the 2.1 Flex Web Service): when it ran, which file, the period it
 * covered ({@code fromDate}/{@code toDate}, as {@code FlexReportParser} already
 * computes them) and the full outcome (counters + row issues + warnings). A
 * write-only log from the domain's point of view — read access is CQRS.
 *
 * <p>{@code fileName} and {@code fromDate} are nullable ({@code
 * MultipartFile.getOriginalFilename()} can be null; Flex itself makes
 * {@code fromDate} optional); {@code toDate} is always required.
 */
public record ImportRecord(Long id, PortfolioId portfolioId, Instant importedAt, String fileName,
                           LocalDate fromDate, LocalDate toDate, int imported, int duplicated,
                           List<ImportRowIssue> errors, List<String> warnings) {

    public ImportRecord {
        if (portfolioId == null) {
            throw new ValidationException("El registro de import requiere una cartera");
        }
        if (importedAt == null) {
            throw new ValidationException("El registro de import requiere fecha/hora");
        }
        if (toDate == null) {
            throw new ValidationException("El registro de import requiere el periodo cubierto (toDate)");
        }
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    /** Builds a new record (identity assigned on persist) from an import's outcome. */
    public static ImportRecord of(PortfolioId portfolioId, Instant importedAt, String fileName,
                                  LocalDate fromDate, LocalDate toDate, int imported, int duplicated,
                                  List<ImportRowIssue> errors, List<String> warnings) {
        return new ImportRecord(null, portfolioId, importedAt, fileName, fromDate, toDate,
                imported, duplicated, errors, warnings);
    }
}
