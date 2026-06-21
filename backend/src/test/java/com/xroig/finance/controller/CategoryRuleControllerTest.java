package com.xroig.finance.controller;

import com.xroig.finance.controller.CategoryRuleController.RuleRequest;
import com.xroig.finance.controller.CategoryRuleController.RuleResponse;
import com.xroig.finance.model.Category;
import com.xroig.finance.model.CategoryRule;
import com.xroig.finance.model.TransactionType;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.CategoryRuleRepository;
import com.xroig.finance.service.RecategorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static com.xroig.finance.Fixtures.category;
import static com.xroig.finance.Fixtures.rule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CategoryRuleController} with mocked repositories and the
 * recategorization service (stage E3d): create/update apply the rule and report
 * how many movements were recategorized; pattern is trimmed; category validated.
 */
@ExtendWith(MockitoExtension.class)
class CategoryRuleControllerTest {

    @Mock private CategoryRuleRepository ruleRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private RecategorizationService recategorizationService;
    @InjectMocks private CategoryRuleController controller;

    private final Category supermercado = category(10, "Supermercado", TransactionType.EXPENSE);

    private static HttpStatus statusOf(Throwable t) {
        return (HttpStatus) ((ResponseStatusException) t).getStatusCode();
    }

    @Test
    void create_savesTrimsPatternAndAppliesRule() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(supermercado));
        when(ruleRepository.save(any(CategoryRule.class))).thenAnswer(i -> i.getArgument(0));
        when(recategorizationService.applyRule(any(CategoryRule.class))).thenReturn(3);

        RuleResponse response = controller.create(new RuleRequest("  lidl|mercadona  ", 10L));

        assertThat(response.recategorized()).isEqualTo(3);
        assertThat(response.rule().getPattern()).isEqualTo("lidl|mercadona");
        assertThat(response.rule().getCategory()).isSameAs(supermercado);
    }

    @Test
    void create_invalidCategoryThrows400() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.create(new RuleRequest("lidl", 10L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void update_notFoundThrows404() {
        when(ruleRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.update(5L, new RuleRequest("lidl", 10L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void update_appliesNewPatternAndCategory() {
        CategoryRule existing = rule(5, "viejo", category(20, "Otros", TransactionType.EXPENSE));
        when(ruleRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(supermercado));
        when(ruleRepository.save(any(CategoryRule.class))).thenAnswer(i -> i.getArgument(0));
        when(recategorizationService.applyRule(any(CategoryRule.class))).thenReturn(1);

        RuleResponse response = controller.update(5L, new RuleRequest("lidl", 10L));

        assertThat(response.recategorized()).isEqualTo(1);
        assertThat(response.rule().getPattern()).isEqualTo("lidl");
        assertThat(response.rule().getCategory()).isSameAs(supermercado);
    }

    @Test
    void delete_delegatesToRepository() {
        controller.delete(5L);
        verify(ruleRepository).deleteById(5L);
    }
}
