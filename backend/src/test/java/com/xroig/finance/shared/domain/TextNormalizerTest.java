package com.xroig.finance.shared.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure tests for {@link TextNormalizer}: lowercases, strips accents, drops the BOM and trims. */
class TextNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            "'  Nómina  ', nomina",
            "'CONSUM', consum",
            "'Categoría', categoria",
    })
    void normalize_lowercasesStripsAccentsAndTrims(String input, String expected) {
        assertThat(TextNormalizer.normalize(input)).isEqualTo(expected);
    }

    @Test
    void normalize_dropsTheUtf8Bom() {
        assertThat(TextNormalizer.normalize("﻿fecha")).isEqualTo("fecha");
    }
}
