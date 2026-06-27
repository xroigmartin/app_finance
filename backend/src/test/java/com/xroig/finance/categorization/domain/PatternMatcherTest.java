package com.xroig.finance.categorization.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-logic tests for the {@link PatternMatcher} domain service: case/accent-insensitive
 * substring matching over {@code |}-separated alternatives, ignoring blank ones.
 */
class PatternMatcherTest {

    @Test
    void matches_isCaseAndAccentInsensitive() {
        assertThat(PatternMatcher.matches("nomina", "Nómina de junio")).isTrue();
    }

    @Test
    void matches_anyOfTheAlternatives() {
        assertThat(PatternMatcher.matches("consum|lidl|spar", "Pago en SPAR centro")).isTrue();
        assertThat(PatternMatcher.matches("consum|lidl|spar", "compra LIDL")).isTrue();
    }

    @Test
    void matches_returnsFalseWhenNoAlternativeIsContained() {
        assertThat(PatternMatcher.matches("lidl", "Mercadona")).isFalse();
    }

    @Test
    void matches_nullDescriptionIsFalse() {
        assertThat(PatternMatcher.matches("lidl", null)).isFalse();
    }

    @Test
    void matches_ignoresBlankAlternatives() {
        assertThat(PatternMatcher.matches("lidl|", "compra lidl")).isTrue();
        assertThat(PatternMatcher.matches("lidl|", "cualquier cosa")).isFalse();
    }
}
