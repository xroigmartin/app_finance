package com.xroig.finance.categorization.domain;

import com.xroig.finance.shared.domain.TextNormalizer;

import java.util.Arrays;

/**
 * Domain service for the categorization matching rule, shared by the recategorization
 * use case and the import (which still lives in the legacy parser and delegates here).
 * A pattern is a set of {@code |}-separated alternatives; it matches a description when
 * <b>any</b> non-blank alternative is contained in it, comparing without case or accents
 * ({@link TextNormalizer}). Blank alternatives (e.g. {@code "lidl|"}) are ignored.
 */
public final class PatternMatcher {

    private PatternMatcher() {
    }

    public static boolean matches(String pattern, String description) {
        if (description == null) {
            return false;
        }
        String normalized = TextNormalizer.normalize(description);
        return Arrays.stream(pattern.split("\\|"))
                .map(TextNormalizer::normalize)
                .filter(p -> !p.isBlank())
                .anyMatch(normalized::contains);
    }
}
