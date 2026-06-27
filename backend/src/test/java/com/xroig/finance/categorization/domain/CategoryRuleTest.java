package com.xroig.finance.categorization.domain;

import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure-domain tests for the {@link CategoryRule} aggregate: the pattern invariant (trimmed,
 * non-blank), identity rules and the matching behaviour delegated to {@link PatternMatcher}.
 */
class CategoryRuleTest {

    private final CategoryId target = new CategoryId(10L);

    @Test
    void create_trimsThePattern() {
        CategoryRule rule = CategoryRule.create("  lidl|mercadona  ", target);
        assertThat(rule.pattern()).isEqualTo("lidl|mercadona");
        assertThat(rule.id()).isNull();
        assertThat(rule.categoryId()).isEqualTo(target);
    }

    @Test
    void create_blankPattern_isRejected() {
        assertThatThrownBy(() -> CategoryRule.create("   ", target))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void changeTo_replacesPatternAndCategory() {
        CategoryRule rule = CategoryRule.rehydrate(new CategoryRuleId(1L), "viejo", target);
        rule.changeTo("  nuevo  ", new CategoryId(20L));
        assertThat(rule.pattern()).isEqualTo("nuevo");
        assertThat(rule.categoryId()).isEqualTo(new CategoryId(20L));
    }

    @Test
    void rehydrate_requiresIdentity() {
        assertThatThrownBy(() -> CategoryRule.rehydrate(null, "lidl", target))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void categoryRuleId_rejectsNull() {
        assertThatThrownBy(() -> new CategoryRuleId(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matches_delegatesToPatternMatcher() {
        CategoryRule rule = CategoryRule.create("nomina", target);
        assertThat(rule.matches("Nómina de junio")).isTrue();
        assertThat(rule.matches("Pago farmacia")).isFalse();
    }
}
