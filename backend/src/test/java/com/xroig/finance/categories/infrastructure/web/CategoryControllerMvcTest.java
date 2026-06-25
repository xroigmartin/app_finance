package com.xroig.finance.categories.infrastructure.web;

import com.xroig.finance.categories.application.CategoryView;
import com.xroig.finance.categories.application.port.CreateCategory;
import com.xroig.finance.categories.application.port.CreateCategory.CreateCategoryCommand;
import com.xroig.finance.categories.application.port.DeleteCategory;
import com.xroig.finance.categories.application.port.FindCategories;
import com.xroig.finance.categories.application.port.UpdateCategory;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.shared.domain.ConflictException;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HTTP-contract test for the migrated {@link CategoryController} (stage H2). The branch
 * logic is verified by {@code CategoryServiceTest}; here we pin what the {@code
 * @WebMvcTest} slice adds: nested-JSON binding of the optional {@code parent}/{@code
 * account} {@code {id}}, bean validation ({@code @NotBlank}/{@code @NotNull} → 400),
 * the {@code @ResponseStatus} codes, the {@link CategoryView} JSON shape, and the
 * domain exceptions reaching the wire as {@code problem+json} (400/404/409).
 */
@WebMvcTest(CategoryController.class)
class CategoryControllerMvcTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private FindCategories findCategories;
    @MockitoBean private CreateCategory createCategory;
    @MockitoBean private UpdateCategory updateCategory;
    @MockitoBean private DeleteCategory deleteCategory;

    private static CategoryView view(Long id, String name, TransactionType type, CategoryView parent) {
        return new CategoryView(id, name, type, "#000", null, parent);
    }

    // ---------- GET ----------

    @Test
    void findAll_returns200WithJsonArray() {
        when(findCategories.all()).thenReturn(List.of(view(1L, "Comida", TransactionType.EXPENSE, null)));

        assertThat(mvc.get().uri("/api/categories"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[0].name").isEqualTo("Comida");
    }

    // ---------- POST: happy paths and nested-JSON binding ----------

    @Test
    void create_topLevel_returns201AsGlobalCategory() {
        when(createCategory.create(any(CreateCategoryCommand.class)))
                .thenReturn(view(1L, "Comida", TransactionType.EXPENSE, null));

        assertThat(mvc.post().uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Comida","type":"EXPENSE"}
                        """))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.account").isNull();
    }

    @Test
    void create_subcategory_bindsNestedParentAndReturnsInheritedType() {
        // The use case inherits the type (EXPENSE) and links the parent; the slice must
        // bind {"parent":{"id":5}} and serialize the nested parent back.
        when(createCategory.create(any(CreateCategoryCommand.class)))
                .thenReturn(view(10L, "Luz", TransactionType.EXPENSE,
                        view(5L, "Hogar", TransactionType.EXPENSE, null)));

        MvcTestResult result = mvc.post().uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Luz","type":"INCOME","parent":{"id":5}}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.parent.id").asNumber().isEqualTo(5);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("EXPENSE");
    }

    @Test
    void create_accountBound_bindsNestedAccountAndReturns201() {
        when(createCategory.create(any(CreateCategoryCommand.class)))
                .thenReturn(new CategoryView(2L, "Comida", TransactionType.EXPENSE, "#000",
                        new CategoryView.AccountRef(1L, "Corriente", "Banco", java.math.BigDecimal.ZERO), null));

        assertThat(mvc.post().uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Comida","type":"EXPENSE","account":{"id":1}}
                        """))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.account.id").asNumber().isEqualTo(1);
    }

    @Test
    void update_valid_returns200WithBody() {
        when(updateCategory.update(anyLong(), any()))
                .thenReturn(view(7L, "Nuevo", TransactionType.INCOME, null));

        assertThat(mvc.put().uri("/api/categories/{id}", 7)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Nuevo","type":"INCOME"}
                        """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.name").isEqualTo("Nuevo");
    }

    // ---------- POST/PUT: bean validation ----------

    @Test
    void create_blankName_returns400AndDoesNotCallUseCase() {
        assertThat(mvc.post().uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"","type":"EXPENSE"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verify(createCategory, never()).create(any());
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

    // ---------- domain exceptions → problem+json ----------

    @Test
    void create_unknownParent_returns400ProblemDetail() {
        when(createCategory.create(any(CreateCategoryCommand.class)))
                .thenThrow(new ValidationException("Categoría principal no válida"));

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
        when(createCategory.create(any(CreateCategoryCommand.class)))
                .thenThrow(new ConflictException(
                        "La categoría principal tiene una recurrencia; quítala antes de añadirle subcategorías"));

        assertThat(mvc.post().uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Luz","type":"EXPENSE","parent":{"id":5}}
                        """))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("La categoría principal tiene una recurrencia; quítala antes de añadirle subcategorías");
    }

    @Test
    void update_notFound_returns404ProblemDetail() {
        when(updateCategory.update(anyLong(), any()))
                .thenThrow(new NotFoundException("Categoría no encontrada"));

        assertThat(mvc.put().uri("/api/categories/{id}", 7)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Comida","type":"EXPENSE"}
                        """))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Categoría no encontrada");
    }

    // ---------- DELETE ----------

    @Test
    void delete_withSubcategories_returns409() {
        doThrow(new ConflictException("La categoría tiene subcategorías y no se puede eliminar"))
                .when(deleteCategory).delete(3L);

        assertThat(mvc.delete().uri("/api/categories/{id}", 3))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("La categoría tiene subcategorías y no se puede eliminar");
    }

    @Test
    void delete_happyPath_returns204() {
        assertThat(mvc.delete().uri("/api/categories/{id}", 3)).hasStatus(HttpStatus.NO_CONTENT);
        verify(deleteCategory).delete(3L);
    }
}
