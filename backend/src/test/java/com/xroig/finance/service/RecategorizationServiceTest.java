package com.xroig.finance.service;

import com.xroig.finance.model.Category;
import com.xroig.finance.model.CategoryRule;
import com.xroig.finance.shared.domain.TransactionType;
import org.junit.jupiter.api.Test;

import static com.xroig.finance.Fixtures.category;
import static com.xroig.finance.Fixtures.rule;
import static org.assertj.core.api.Assertions.assertThat;

/** Pure-logic tests for {@link RecategorizationService#matches}. The applyRule
 *  behaviour (with mocked repositories) is added in stage E2c. */
class RecategorizationServiceTest {

    private final Category expenseCat = category(1, "Supermercado", TransactionType.EXPENSE);

    @Test
    void matches_isCaseAndAccentInsensitive() {
        CategoryRule r = rule(1, "nomina", expenseCat);
        assertThat(RecategorizationService.matches(r, "Nómina de junio")).isTrue();
    }

    @Test
    void matches_anyOfTheAlternatives() {
        CategoryRule r = rule(1, "consum|lidl|spar", expenseCat);
        assertThat(RecategorizationService.matches(r, "Pago en SPAR centro")).isTrue();
        assertThat(RecategorizationService.matches(r, "compra LIDL")).isTrue();
    }

    @Test
    void matches_returnsFalseWhenNoAlternativeIsContained() {
        CategoryRule r = rule(1, "lidl", expenseCat);
        assertThat(RecategorizationService.matches(r, "Mercadona")).isFalse();
    }

    @Test
    void matches_nullDescriptionIsFalse() {
        CategoryRule r = rule(1, "lidl", expenseCat);
        assertThat(RecategorizationService.matches(r, null)).isFalse();
    }

    @Test
    void matches_ignoresBlankAlternatives() {
        CategoryRule r = rule(1, "lidl|", expenseCat);
        assertThat(RecategorizationService.matches(r, "compra lidl")).isTrue();
        assertThat(RecategorizationService.matches(r, "cualquier cosa")).isFalse();
    }
}
