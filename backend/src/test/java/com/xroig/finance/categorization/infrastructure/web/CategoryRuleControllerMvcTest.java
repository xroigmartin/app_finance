package com.xroig.finance.categorization.infrastructure.web;

import com.xroig.finance.categories.application.CategoryView;
import com.xroig.finance.categorization.application.CategoryRuleView;
import com.xroig.finance.categorization.application.RuleSaved;
import com.xroig.finance.categorization.application.port.CreateRule;
import com.xroig.finance.categorization.application.port.CreateRule.RuleCommand;
import com.xroig.finance.categorization.application.port.DeleteRule;
import com.xroig.finance.categorization.application.port.FindRules;
import com.xroig.finance.categorization.application.port.UpdateRule;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HTTP-contract test for the migrated {@link CategoryRuleController} (stage H6). The branch
 * logic is verified by {@code CategoryRuleServiceTest}; here we pin what the {@code
 * @WebMvcTest} slice adds: bean validation on {@link CategoryRuleRequest}
 * ({@code @NotBlank pattern}/{@code @NotNull categoryId} → 400), the {@link RuleSaved} JSON
 * shape ({@code {rule, recategorized}}), the {@code @ResponseStatus} codes, and the domain
 * exceptions as {@code problem+json} (unknown category → 400, not found → 404).
 */
@WebMvcTest(CategoryRuleController.class)
class CategoryRuleControllerMvcTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private FindRules findRules;
    @MockitoBean private CreateRule createRule;
    @MockitoBean private UpdateRule updateRule;
    @MockitoBean private DeleteRule deleteRule;

    private static CategoryRuleView view(String pattern) {
        CategoryView category = new CategoryView(2L, "Comida", TransactionType.EXPENSE, "#000000", null, null);
        return new CategoryRuleView(1L, pattern, category);
    }

    @Test
    void findAll_returns200WithJsonArray() {
        when(findRules.findAll()).thenReturn(List.of(view("mercadona")));

        assertThat(mvc.get().uri("/api/category-rules"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[0].pattern").isEqualTo("mercadona");
    }

    @Test
    void create_valid_returns201WithRuleAndRecategorizedCount() {
        when(createRule.create(any(RuleCommand.class))).thenReturn(new RuleSaved(view("mercadona"), 3));

        assertThat(mvc.post().uri("/api/category-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pattern":"mercadona","categoryId":2}
                        """))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.recategorized").asNumber().isEqualTo(3);
    }

    @Test
    void create_blankPattern_returns400AndDoesNotCreate() {
        assertThat(mvc.post().uri("/api/category-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pattern":"   ","categoryId":2}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verify(createRule, never()).create(any());
    }

    @Test
    void create_nullCategoryId_returns400() {
        assertThat(mvc.post().uri("/api/category-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pattern":"mercadona"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_malformedJson_returns400() {
        assertThat(mvc.post().uri("/api/category-rules")
                .contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_unknownCategory_returns400ProblemDetail() {
        when(createRule.create(any(RuleCommand.class)))
                .thenThrow(new ValidationException("Categoría no válida"));

        assertThat(mvc.post().uri("/api/category-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pattern":"mercadona","categoryId":2}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail").isEqualTo("Categoría no válida");
    }

    @Test
    void update_notFound_returns404ProblemDetail() {
        when(updateRule.update(anyLong(), any()))
                .thenThrow(new NotFoundException("Regla no encontrada"));

        assertThat(mvc.put().uri("/api/category-rules/{id}", 99)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pattern":"mercadona","categoryId":2}
                        """))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Regla no encontrada");
    }

    @Test
    void update_valid_returns200WithBody() {
        when(updateRule.update(anyLong(), any())).thenReturn(new RuleSaved(view("lidl"), 1));

        assertThat(mvc.put().uri("/api/category-rules/{id}", 5)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pattern":"lidl","categoryId":2}
                        """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.rule.pattern").isEqualTo("lidl");
    }

    @Test
    void delete_returns204AndDelegates() {
        assertThat(mvc.delete().uri("/api/category-rules/{id}", 5)).hasStatus(HttpStatus.NO_CONTENT);
        verify(deleteRule).delete(5L);
    }
}
