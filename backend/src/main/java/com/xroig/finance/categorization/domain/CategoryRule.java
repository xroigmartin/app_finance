package com.xroig.finance.categorization.domain;

import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.shared.domain.ValidationException;

import java.util.Objects;

/**
 * Category-rule aggregate root: a text pattern that auto-assigns a target category to
 * movements that arrive without one. Pure of any framework or persistence concern; the
 * target category is referenced by identity ({@link CategoryId}).
 *
 * <p>Invariant kept here: the pattern is non-blank (it is trimmed on the way in). The
 * matching behaviour ({@link #matches(String)}) is delegated to the {@link PatternMatcher}
 * domain service so the import and the recategorization match identically. Which category
 * the rule targets — its type, account scope and the fallback it pulls from — is resolved
 * by the application service, never by navigating objects.
 */
public class CategoryRule {

    private final CategoryRuleId id;
    private String pattern;
    private CategoryId categoryId;

    private CategoryRule(CategoryRuleId id, String pattern, CategoryId categoryId) {
        this.id = id;
        this.pattern = requirePattern(pattern);
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
    }

    /** Factory for a new rule (identity assigned on persist); trims the pattern. */
    public static CategoryRule create(String pattern, CategoryId categoryId) {
        return new CategoryRule(null, pattern, categoryId);
    }

    /** Rebuilds a persisted rule (identity present), from the persistence adapter. */
    public static CategoryRule rehydrate(CategoryRuleId id, String pattern, CategoryId categoryId) {
        if (id == null) {
            throw new IllegalArgumentException("Una regla rehidratada necesita identidad");
        }
        return new CategoryRule(id, pattern, categoryId);
    }

    /** Re-applies the editable fields (pattern + target category), re-checking the invariant. */
    public void changeTo(String pattern, CategoryId categoryId) {
        this.pattern = requirePattern(pattern);
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
    }

    /** Whether this rule's pattern matches the given description (case/accent-insensitive). */
    public boolean matches(String description) {
        return PatternMatcher.matches(pattern, description);
    }

    public CategoryRuleId id() {
        return id;
    }

    public String pattern() {
        return pattern;
    }

    public CategoryId categoryId() {
        return categoryId;
    }

    private static String requirePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new ValidationException("El patrón de la regla no puede estar vacío");
        }
        return pattern.trim();
    }
}
