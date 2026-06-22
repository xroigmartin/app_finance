package com.xroig.finance.controller;

import com.xroig.finance.model.CategoryRule;
import com.xroig.finance.model.TransactionType;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.CategoryRuleRepository;
import com.xroig.finance.service.RecategorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.List;
import java.util.Optional;

import static com.xroig.finance.Fixtures.category;
import static com.xroig.finance.Fixtures.rule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Level-3 HTTP-contract test (stage M4) for {@link CategoryRuleController}. The
 * matching/applyRule logic is in the Mockito test {@code CategoryRuleControllerTest};
 * here we pin what the {@code @WebMvcTest} slice adds: the {@code RuleRequest}
 * validations ({@code @NotBlank pattern}/{@code @NotNull categoryId} → 400), the
 * {@code RuleResponse} JSON shape ({@code {rule, recategorized}}), the
 * {@code @ResponseStatus} codes, and the {@code ResponseStatusException} →
 * {@code application/problem+json} mappings (unknown category → 400, not found → 404).
 */
@WebMvcTest(CategoryRuleController.class)
class CategoryRuleControllerMvcTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private CategoryRuleRepository ruleRepository;
    @MockitoBean private CategoryRepository categoryRepository;
    @MockitoBean private RecategorizationService recategorizationService;

    @Test
    void findAll_returns200WithJsonArray() {
        when(ruleRepository.findAll())
                .thenReturn(List.of(rule(1, "mercadona", category(2, "Comida", TransactionType.EXPENSE))));

        assertThat(mvc.get().uri("/api/category-rules"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[0].pattern").isEqualTo("mercadona");
    }

    @Test
    void create_valid_returns201WithTrimmedPatternAndRecategorizedCount() {
        when(categoryRepository.findById(2L))
                .thenReturn(Optional.of(category(2, "Comida", TransactionType.EXPENSE)));
        when(ruleRepository.save(any(CategoryRule.class))).thenAnswer(i -> i.getArgument(0));
        when(recategorizationService.applyRule(any(CategoryRule.class))).thenReturn(3);

        MvcTestResult result = mvc.post().uri("/api/category-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pattern":"  mercadona  ","categoryId":2}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.recategorized").asNumber().isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.rule.pattern").isEqualTo("mercadona"); // trimmed
    }

    @Test
    void create_blankPattern_returns400AndDoesNotSave() {
        assertThat(mvc.post().uri("/api/category-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pattern":"   ","categoryId":2}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verify(ruleRepository, never()).save(any());
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
        when(categoryRepository.findById(2L)).thenReturn(Optional.empty());

        assertThat(mvc.post().uri("/api/category-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pattern":"mercadona","categoryId":2}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").isEqualTo("Categoría no válida");
    }

    @Test
    void update_notFound_returns404ProblemDetail() {
        when(ruleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(mvc.put().uri("/api/category-rules/{id}", 99)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pattern":"mercadona","categoryId":2}
                        """))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Regla no encontrada");
    }

    @Test
    void delete_returns204AndDelegates() {
        assertThat(mvc.delete().uri("/api/category-rules/{id}", 5)).hasStatus(HttpStatus.NO_CONTENT);
        verify(ruleRepository).deleteById(5L);
    }
}
