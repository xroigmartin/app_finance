package com.xroig.finance.imports.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * A single parsed file row, keyed by normalized header name (lowercase, accents
 * stripped) as produced by the file reader. It speaks the language of an imported
 * row: looking columns up by any of their accepted aliases and parsing the bank
 * formats for amounts and dates.
 *
 * <p>Pure value type (no Spring/JPA). Parsing failures raise {@link
 * IllegalArgumentException} so the use case can turn them into per-row errors with
 * the offending text, exactly as the legacy parser did.
 */
public final class ImportRow {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"));

    private final Map<String, String> cells;

    public ImportRow(Map<String, String> cells) {
        this.cells = cells;
    }

    /** True when the column is present (even if blank); used to detect a transfers file. */
    public boolean has(String key) {
        return cells.containsKey(key);
    }

    /** First non-blank value among the given column aliases, or {@code null}. */
    public String value(String... keys) {
        for (String key : keys) {
            String value = cells.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /** Like {@link #value} but raises when the column is missing/blank. */
    public String required(String... keys) {
        String value = value(keys);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta la columna \"" + keys[0] + "\"");
        }
        return value;
    }

    public static LocalDate parseDate(String value) {
        String v = value.trim();
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(v, format);
            } catch (Exception ignored) {
                // try the next accepted format
            }
        }
        throw new IllegalArgumentException("Fecha no válida: \"" + value + "\"");
    }

    /**
     * Accepts "1234.56", "1.234,56", "1234,56", "-12 €"…
     */
    public static BigDecimal parseAmount(String value) {
        String v = value.replaceAll("[€$\\s]", "");
        if (v.isEmpty()) {
            throw new IllegalArgumentException("Importe vacío");
        }
        int lastComma = v.lastIndexOf(',');
        int lastDot = v.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            if (lastComma > lastDot) {
                v = v.replace(".", "").replace(',', '.');
            } else {
                v = v.replace(",", "");
            }
        } else if (lastComma >= 0) {
            v = v.replace(',', '.');
        }
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Importe no válido: \"" + value + "\"");
        }
    }
}
