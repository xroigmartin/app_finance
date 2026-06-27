package com.xroig.finance.imports.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain tests for {@link ImportRow}: the bank amount/date formats and the column
 * lookup by aliases (value/required/has).
 */
class ImportRowTest {

    // --- parseAmount ---

    @ParameterizedTest
    @CsvSource({
            "1234.56, 1234.56",
            "'1.234,56', 1234.56",
            "'1234,56', 1234.56",
            "'1,234.56', 1234.56",
            "'-12 €', -12",
            "'  10 ', 10"
    })
    void parseAmount_acceptsCommonFormats(String input, String expected) {
        assertThat(ImportRow.parseAmount(input)).isEqualByComparingTo(new BigDecimal(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abc", "1.2.3,4,5x"})
    void parseAmount_rejectsInvalid(String input) {
        assertThatThrownBy(() -> ImportRow.parseAmount(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- parseDate ---

    @ParameterizedTest
    @CsvSource({
            "2026-06-15, 2026-06-15",
            "15/06/2026, 2026-06-15",
            "15-06-2026, 2026-06-15",
            "15/06/26, 2026-06-15"
    })
    void parseDate_acceptsKnownFormats(String input, String expected) {
        assertThat(ImportRow.parseDate(input)).isEqualTo(LocalDate.parse(expected));
    }

    @Test
    void parseDate_rejectsInvalid() {
        assertThatThrownBy(() -> ImportRow.parseDate("no-soy-fecha"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- column lookup ---

    @Test
    void value_returnsFirstNonBlankAliasAndHasChecksPresence() {
        ImportRow row = row("descripcion", "  ", "concepto", "Compra LIDL");

        assertThat(row.value("descripcion", "concepto")).isEqualTo("Compra LIDL");
        assertThat(row.value("inexistente")).isNull();
        assertThat(row.has("descripcion")).isTrue();
        assertThat(row.has("inexistente")).isFalse();
    }

    @Test
    void required_raisesWhenMissingNamingTheFirstAlias() {
        ImportRow row = row("fecha", "2026-06-15");

        assertThat(row.required("fecha", "date")).isEqualTo("2026-06-15");
        assertThatThrownBy(() -> row.required("importe", "amount"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("importe");
    }

    private static ImportRow row(String... kv) {
        Map<String, String> cells = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            cells.put(kv[i], kv[i + 1]);
        }
        return new ImportRow(cells);
    }
}
