package com.xroig.finance.controller;

import com.xroig.finance.model.Category;
import com.xroig.finance.model.TransactionType;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.BudgetRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.CategoryRuleRepository;
import com.xroig.finance.repository.RecurringBudgetRepository;
import com.xroig.finance.repository.TransactionRepository;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Level-3 HTTP-contract test (stage M2) for {@link CategoryController}. The whole
 * branch logic (parent/subcategory resolution, scope rules, recurrence and delete
 * guards) is already verified by the Mockito test {@code CategoryControllerTest};
 * here we pin only what the {@code @WebMvcTest} slice adds: nested-JSON binding of
 * the optional {@code parent {id}} / {@code account {id}}, bean validation
 * ({@code @NotBlank name}/{@code @NotNull type} → 400), the {@code @ResponseStatus}
 * codes, and that each {@link org.springframework.web.server.ResponseStatusException}
 * reaches the wire as {@code application/problem+json} with its Spanish {@code detail}
 * (representative 400/404/409 mappings).
 */
@WebMvcTest(CategoryController.class)
class CategoryControllerMvcTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private CategoryRepository categoryRepository;
    @MockitoBean private TransactionRepository transactionRepository;
    @MockitoBean private BudgetRepository budgetRepository;
    @MockitoBean private CategoryRuleRepository ruleRepository;
    @MockitoBean private AccountRepository accountRepository;
    @MockitoBean private RecurringBudgetRepository recurringBudgetRepository;

    // ---------- GET ----------

    @Test
    void findAll_returns200WithJsonArray() {
        when(categoryRepository.findAll()).thenReturn(List.of(category(1, "Comida", TransactionType.EXPENSE)));

        assertThat(mvc.get().uri("/api/categories"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[0].name").isEqualTo("Comida");
    }

    // ---------- POST: happy paths and nested-JSON binding ----------

    @Test
    void create_topLevel_returns201AsGlobalCategory() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(mvc.post().uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Comida","type":"EXPENSE"}
                        """))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.account").isNull();
    }

    @Test
    void create_subcategory_bindsNestedParentAndInheritsType() {
        // Global top-level parent (no recurrence) → the child binds to it and
        // inherits its type; the nested {"parent":{"id":5}} must round-trip.
        when(categoryRepository.findById(5L))
                .thenReturn(Optional.of(category(5, "Hogar", TransactionType.EXPENSE)));
        when(recurringBudgetRepository.existsByCategoryId(5L)).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        MvcTestResult result = mvc.post().uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Luz","type":"INCOME","parent":{"id":5}}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.parent.id").asNumber().isEqualTo(5);
        // type inherited from the parent (EXPENSE), not the body's INCOME
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("EXPENSE");
    }

    // ---------- POST/PUT: bean validation ----------

    @Test
    void create_blankName_returns400AndDoesNotSave() {
        assertThat(mvc.post().uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"","type":"EXPENSE"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void create_nullType_returns400() {
        assertThat(mvc.post().uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Comida"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_malformedJson_returns400() {
        assertThat(mvc.post().uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    // ---------- representative ResponseStatusException → problem+json ----------

    @Test
    void create_unknownParent_returns400ProblemDetail() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(mvc.post().uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Luz","type":"EXPENSE","parent":{"id":99}}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail").isEqualTo("Categoría principal no válida");
    }

    @Test
    void create_subcategoryUnderParentWithRecurrence_returns409() {
        when(categoryRepository.findById(5L))
                .thenReturn(Optional.of(category(5, "Hogar", TransactionType.EXPENSE)));
        when(recurringBudgetRepository.existsByCategoryId(5L)).thenReturn(true);

        assertThat(mvc.post().uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Luz","type":"EXPENSE","parent":{"id":5}}
                        """))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("La categoría principal tiene una recurrencia; quítala antes de añadirle subcategorías");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void update_notFound_returns404ProblemDetail() {
        when(categoryRepository.findById(7L)).thenReturn(Optional.empty());

        assertThat(mvc.put().uri("/api/categories/{id}", 7)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Comida","type":"EXPENSE"}
                        """))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Categoría no encontrada");
    }

    // ---------- DELETE: guards and status ----------

    @Test
    void delete_withSubcategories_returns409() {
        when(categoryRepository.existsByParentId(3L)).thenReturn(true);

        assertThat(mvc.delete().uri("/api/categories/{id}", 3))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("La categoría tiene subcategorías y no se puede eliminar");
        verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    void delete_happyPath_returns204() {
        when(categoryRepository.existsByParentId(3L)).thenReturn(false);
        when(transactionRepository.existsByCategoryId(3L)).thenReturn(false);
        when(budgetRepository.existsByCategoryId(3L)).thenReturn(false);
        when(ruleRepository.existsByCategoryId(3L)).thenReturn(false);

        assertThat(mvc.delete().uri("/api/categories/{id}", 3)).hasStatus(HttpStatus.NO_CONTENT);
        verify(categoryRepository).deleteById(3L);
    }
}
