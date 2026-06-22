package com.xroig.finance.controller;

import com.xroig.finance.model.Budget;
import com.xroig.finance.model.TransactionType;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.BudgetRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.service.BudgetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.xroig.finance.Fixtures.account;
import static com.xroig.finance.Fixtures.budget;
import static com.xroig.finance.Fixtures.category;
import static com.xroig.finance.Fixtures.eur;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Level-3 HTTP-contract test (stage M3) for {@link BudgetController}. The branch
 * logic (leaf/parent rejection, cross-account, duplicate skip in copy) lives in
 * the Mockito test {@code BudgetControllerTest}; here we pin what the
 * {@code @WebMvcTest} slice adds: query-parameter binding with the current-date
 * fallback, the record validations on {@code BudgetRequest}/{@code CopyRequest}
 * ({@code @NotNull}/{@code @Positive}/{@code @Min(1)}/{@code @Max(12)} → 400), the
 * {@code @ResponseStatus} codes, and the {@code ResponseStatusException} →
 * {@code application/problem+json} mappings (409 duplicate, 400 parent category,
 * 404 not found).
 */
@WebMvcTest(BudgetController.class)
class BudgetControllerMvcTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private BudgetRepository budgetRepository;
    @MockitoBean private CategoryRepository categoryRepository;
    @MockitoBean private AccountRepository accountRepository;
    @MockitoBean private BudgetService budgetService;

    private static final String VALID_BODY = """
            {"accountId":1,"categoryId":2,"year":2024,"month":3,"amount":100}
            """;

    // ---------- GET: query-parameter binding ----------

    @Test
    void find_byAccount_bindsParams() {
        when(budgetRepository.findByAccountIdAndYearAndMonth(1L, 2024, 3)).thenReturn(List.of());

        assertThat(mvc.get().uri("/api/budgets?year=2024&month=3&accountId=1")).hasStatusOk();
        verify(budgetRepository).findByAccountIdAndYearAndMonth(1L, 2024, 3);
    }

    @Test
    void find_withoutParams_fallsBackToCurrentYearMonthAndAllAccounts() {
        LocalDate now = LocalDate.now();
        when(budgetRepository.findByYearAndMonth(now.getYear(), now.getMonthValue())).thenReturn(List.of());

        assertThat(mvc.get().uri("/api/budgets")).hasStatusOk();
        verify(budgetRepository).findByYearAndMonth(now.getYear(), now.getMonthValue());
    }

    @Test
    void annual_bindsYearAndAccount() {
        assertThat(mvc.get().uri("/api/budgets/annual?year=2024&accountId=1")).hasStatusOk();
        verify(budgetService).annual(2024, 1L);
    }

    @Test
    void annual_withoutYear_usesCurrentYear() {
        assertThat(mvc.get().uri("/api/budgets/annual")).hasStatusOk();
        verify(budgetService).annual(eq(LocalDate.now().getYear()), isNull());
    }

    // ---------- POST: happy path, validation, business mappings ----------

    @Test
    void create_valid_returns201() {
        when(budgetRepository.existsByAccountIdAndCategoryIdAndYearAndMonth(1L, 2L, 2024, 3)).thenReturn(false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(1, "Corriente")));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category(2, "Comida", TransactionType.EXPENSE)));
        when(categoryRepository.existsByParentId(2L)).thenReturn(false);
        when(budgetRepository.save(any(Budget.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(mvc.post().uri("/api/budgets")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.amount").asNumber().isEqualTo(100);
    }

    @ParameterizedTest(name = "invalid BudgetRequest → 400: {0}")
    @ValueSource(strings = {
            "{\"accountId\":1,\"categoryId\":2,\"year\":2024,\"month\":0,\"amount\":100}",   // @Min(1)
            "{\"accountId\":1,\"categoryId\":2,\"year\":2024,\"month\":13,\"amount\":100}",  // @Max(12)
            "{\"accountId\":1,\"categoryId\":2,\"year\":2024,\"month\":3,\"amount\":0}",     // @Positive
            "{\"accountId\":1,\"categoryId\":2,\"year\":2024,\"month\":3,\"amount\":-5}",    // @Positive
            "{\"categoryId\":2,\"year\":2024,\"month\":3,\"amount\":100}",                    // null accountId
            "{\"accountId\":1,\"year\":2024,\"month\":3,\"amount\":100}",                     // null categoryId
            "{\"accountId\":1,\"categoryId\":2,\"month\":3,\"amount\":100}",                  // null year
    })
    void create_invalidBody_returns400AndDoesNotSave(String body) {
        assertThat(mvc.post().uri("/api/budgets")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void create_malformedJson_returns400() {
        assertThat(mvc.post().uri("/api/budgets")
                .contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_duplicate_returns409ProblemDetail() {
        when(budgetRepository.existsByAccountIdAndCategoryIdAndYearAndMonth(1L, 2L, 2024, 3)).thenReturn(true);

        assertThat(mvc.post().uri("/api/budgets")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("La categoría ya tiene presupuesto en ese mes para esta cuenta");
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void create_onParentCategory_returns400ProblemDetail() {
        when(budgetRepository.existsByAccountIdAndCategoryIdAndYearAndMonth(1L, 2L, 2024, 3)).thenReturn(false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(1, "Corriente")));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category(2, "Hogar", TransactionType.EXPENSE)));
        when(categoryRepository.existsByParentId(2L)).thenReturn(true); // has subcategories

        assertThat(mvc.post().uri("/api/budgets")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("No se puede presupuestar una categoría con subcategorías; presupuesta sus subcategorías");
    }

    // ---------- PUT / DELETE ----------

    @Test
    void update_notFound_returns404ProblemDetail() {
        when(budgetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(mvc.put().uri("/api/budgets/{id}", 99)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Presupuesto no encontrado");
    }

    @Test
    void delete_returns204AndDelegates() {
        assertThat(mvc.delete().uri("/api/budgets/{id}", 5)).hasStatus(HttpStatus.NO_CONTENT);
        verify(budgetRepository).deleteById(5L);
    }

    // ---------- POST /copy ----------

    @Test
    void copy_valid_returns200WithCopiedList() {
        Budget source = budget(1L, account(1, "Corriente"),
                category(2, "Comida", TransactionType.EXPENSE), 2024, 1, eur("100"));
        when(budgetRepository.findByYearAndMonth(2024, 1)).thenReturn(List.of(source));
        when(budgetRepository.existsByAccountIdAndCategoryIdAndYearAndMonth(1L, 2L, 2024, 2)).thenReturn(false);
        when(budgetRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(mvc.post().uri("/api/budgets/copy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fromYear":2024,"fromMonth":1,"toYear":2024,"toMonth":2}
                        """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.length()").asNumber().isEqualTo(1);
    }

    @Test
    void copy_invalidMonth_returns400() {
        assertThat(mvc.post().uri("/api/budgets/copy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fromYear":2024,"fromMonth":13,"toYear":2024,"toMonth":2}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verify(budgetRepository, never()).saveAll(any());
    }
}
