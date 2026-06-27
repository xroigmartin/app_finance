package com.xroig.finance.budgets.infrastructure.web;

import com.xroig.finance.budgets.application.AnnualBudgetView;
import com.xroig.finance.budgets.application.BudgetView;
import com.xroig.finance.budgets.application.port.CopyBudgets;
import com.xroig.finance.budgets.application.port.CreateBudget;
import com.xroig.finance.budgets.application.port.CreateBudget.BudgetCommand;
import com.xroig.finance.budgets.application.port.DeleteBudget;
import com.xroig.finance.budgets.application.port.FindBudgets;
import com.xroig.finance.budgets.application.port.UpdateBudget;
import com.xroig.finance.shared.domain.ConflictException;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HTTP-contract test for the migrated {@link BudgetController} (stage H5a). The branch
 * logic is verified by {@code BudgetServiceTest}; here we pin what the {@code @WebMvcTest}
 * slice adds: query-parameter binding with the current-date fallback, the bean validation
 * on {@link BudgetRequest}/{@link CopyRequest} (→ 400), the {@code @ResponseStatus} codes,
 * the {@link BudgetView}/{@link AnnualBudgetView} JSON, and the domain exceptions as
 * {@code problem+json} (duplicate → 409, parent category → 400, not found → 404).
 */
@WebMvcTest(BudgetController.class)
class BudgetControllerMvcTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private FindBudgets findBudgets;
    @MockitoBean private CreateBudget createBudget;
    @MockitoBean private UpdateBudget updateBudget;
    @MockitoBean private DeleteBudget deleteBudget;
    @MockitoBean private CopyBudgets copyBudgets;

    private static final String VALID_BODY = """
            {"accountId":1,"categoryId":2,"year":2024,"month":3,"amount":100}
            """;

    private static BudgetView view() {
        return new BudgetView(7L, null, null, 2024, 3, new BigDecimal("100"));
    }

    // ---------- GET: query-parameter binding ----------

    @Test
    void find_byAccount_bindsParams() {
        when(findBudgets.find(2024, 3, 1L)).thenReturn(List.of(view()));

        assertThat(mvc.get().uri("/api/budgets?year=2024&month=3&accountId=1"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[0].amount").asNumber().isEqualTo(100);
        verify(findBudgets).find(2024, 3, 1L);
    }

    @Test
    void find_withoutParams_fallsBackToCurrentYearMonthAndAllAccounts() {
        LocalDate now = LocalDate.now();
        when(findBudgets.find(now.getYear(), now.getMonthValue(), null)).thenReturn(List.of());

        assertThat(mvc.get().uri("/api/budgets")).hasStatusOk();
        verify(findBudgets).find(now.getYear(), now.getMonthValue(), null);
    }

    @Test
    void annual_bindsYearAndAccount() {
        when(findBudgets.annual(2024, 1L)).thenReturn(new AnnualBudgetView(2024, 1L, List.of(), List.of()));

        assertThat(mvc.get().uri("/api/budgets/annual?year=2024&accountId=1")).hasStatusOk();
        verify(findBudgets).annual(2024, 1L);
    }

    @Test
    void annual_withoutYear_usesCurrentYear() {
        when(findBudgets.annual(eq(LocalDate.now().getYear()), isNull()))
                .thenReturn(new AnnualBudgetView(LocalDate.now().getYear(), null, List.of(), List.of()));

        assertThat(mvc.get().uri("/api/budgets/annual")).hasStatusOk();
        verify(findBudgets).annual(eq(LocalDate.now().getYear()), isNull());
    }

    // ---------- POST: happy path, validation, business mappings ----------

    @Test
    void create_valid_returns201() {
        when(createBudget.create(any(BudgetCommand.class))).thenReturn(view());

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
    void create_invalidBody_returns400AndDoesNotCreate(String body) {
        assertThat(mvc.post().uri("/api/budgets")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verify(createBudget, never()).create(any());
    }

    @Test
    void create_malformedJson_returns400() {
        assertThat(mvc.post().uri("/api/budgets")
                .contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_duplicate_returns409ProblemDetail() {
        when(createBudget.create(any(BudgetCommand.class)))
                .thenThrow(new ConflictException("La categoría ya tiene presupuesto en ese mes para esta cuenta"));

        assertThat(mvc.post().uri("/api/budgets")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("La categoría ya tiene presupuesto en ese mes para esta cuenta");
    }

    @Test
    void create_onParentCategory_returns400ProblemDetail() {
        when(createBudget.create(any(BudgetCommand.class)))
                .thenThrow(new ValidationException(
                        "No se puede presupuestar una categoría con subcategorías; presupuesta sus subcategorías"));

        assertThat(mvc.post().uri("/api/budgets")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("No se puede presupuestar una categoría con subcategorías; presupuesta sus subcategorías");
    }

    // ---------- PUT / DELETE ----------

    @Test
    void update_notFound_returns404ProblemDetail() {
        when(updateBudget.update(anyLong(), any()))
                .thenThrow(new NotFoundException("Presupuesto no encontrado"));

        assertThat(mvc.put().uri("/api/budgets/{id}", 99)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Presupuesto no encontrado");
    }

    @Test
    void update_valid_returns200WithBody() {
        when(updateBudget.update(anyLong(), any())).thenReturn(view());

        assertThat(mvc.put().uri("/api/budgets/{id}", 5)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatusOk()
                .bodyJson().extractingPath("$.amount").asNumber().isEqualTo(100);
    }

    @Test
    void delete_returns204AndDelegates() {
        assertThat(mvc.delete().uri("/api/budgets/{id}", 5)).hasStatus(HttpStatus.NO_CONTENT);
        verify(deleteBudget).delete(5L);
    }

    // ---------- POST /copy ----------

    @Test
    void copy_valid_returns200WithCopiedList() {
        when(copyBudgets.copy(any())).thenReturn(List.of(view()));

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
        verify(copyBudgets, never()).copy(any());
    }
}
