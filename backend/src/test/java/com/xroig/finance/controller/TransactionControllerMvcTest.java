package com.xroig.finance.controller;

import com.xroig.finance.model.Account;
import com.xroig.finance.model.Category;
import com.xroig.finance.model.Transaction;
import com.xroig.finance.model.TransactionType;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.TransactionRepository;
import com.xroig.finance.service.ImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
import static com.xroig.finance.Fixtures.category;
import static com.xroig.finance.Fixtures.eur;
import static com.xroig.finance.Fixtures.expense;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Level-3 HTTP-contract test (stage M1) for {@link TransactionController}, the
 * controller with the widest HTTP surface. It pins what only the {@code @WebMvcTest}
 * slice can: query-parameter binding (including the default date window and a
 * malformed date → 400), bean validation on the {@code TransactionRequest} record
 * ({@code @NotNull}/{@code @Positive} → 400), the {@code @ResponseStatus} codes,
 * and that {@link org.springframework.web.server.ResponseStatusException} surfaces
 * as {@code application/problem+json} with its Spanish {@code detail}
 * ({@code spring.mvc.problemdetails.enabled=true}). The branch logic of
 * {@code apply}/{@code applyRefund} stays in the Mockito test {@code TransactionControllerTest}.
 */
@WebMvcTest(TransactionController.class)
class TransactionControllerMvcTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private TransactionRepository transactionRepository;
    @MockitoBean private AccountRepository accountRepository;
    @MockitoBean private CategoryRepository categoryRepository;
    @MockitoBean private ImportService importService;

    private static final Account ACCOUNT = account(1, "Corriente");
    private static final Category GLOBAL_CAT = category(2, "Comida", TransactionType.EXPENSE);

    private static final String VALID_BODY = """
            {"date":"2024-01-15","amount":50,"type":"EXPENSE","accountId":1,"categoryId":2}
            """;

    // ---------- GET: query-parameter binding ----------

    @Test
    void find_bindsAllFilters_andReturnsJson() {
        when(transactionRepository.search(any(), any(), any(), any()))
                .thenReturn(List.of(expense(7L, eur("50"), ACCOUNT, GLOBAL_CAT, LocalDate.of(2024, 1, 15))));

        assertThat(mvc.get().uri("/api/transactions?from=2024-01-01&to=2024-01-31&accountId=1&categoryId=2"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[0].amount").asNumber().isEqualTo(50);

        verify(transactionRepository).search(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), 1L, 2L);
    }

    @Test
    void find_withoutParams_appliesWideDefaultDateWindow() {
        when(transactionRepository.search(any(), any(), isNull(), isNull())).thenReturn(List.of());

        assertThat(mvc.get().uri("/api/transactions")).hasStatusOk();

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(transactionRepository).search(from.capture(), to.capture(), isNull(), isNull());
        assertThat(from.getValue()).isEqualTo(LocalDate.of(1970, 1, 1));
        assertThat(to.getValue()).isEqualTo(LocalDate.of(2999, 12, 31));
    }

    @Test
    void find_withMalformedDate_returns400() {
        assertThat(mvc.get().uri("/api/transactions?from=no-es-fecha")).hasStatus(HttpStatus.BAD_REQUEST);
        verify(transactionRepository, never()).search(any(), any(), any(), any());
    }

    @Test
    void recent_returns200WithJsonArray() {
        when(transactionRepository.findTop10ByOrderByDateDescIdDesc())
                .thenReturn(List.of(expense(7L, eur("50"), ACCOUNT, GLOBAL_CAT, LocalDate.of(2024, 1, 15))));

        assertThat(mvc.get().uri("/api/transactions/recent"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.length()").asNumber().isEqualTo(1);
    }

    // ---------- POST: happy path and bean validation ----------

    @Test
    void create_valid_returns201WithJson() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ACCOUNT));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(GLOBAL_CAT));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(mvc.post().uri("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.type").isEqualTo("EXPENSE");
    }

    @ParameterizedTest(name = "invalid body → 400: {0}")
    @ValueSource(strings = {
            "{\"date\":\"2024-01-15\",\"amount\":-5,\"type\":\"EXPENSE\",\"accountId\":1,\"categoryId\":2}", // @Positive
            "{\"date\":\"2024-01-15\",\"amount\":0,\"type\":\"EXPENSE\",\"accountId\":1,\"categoryId\":2}",  // @Positive (0)
            "{\"amount\":50,\"type\":\"EXPENSE\",\"accountId\":1,\"categoryId\":2}",                          // null date
            "{\"date\":\"2024-01-15\",\"type\":\"EXPENSE\",\"accountId\":1,\"categoryId\":2}",                // null amount
            "{\"date\":\"2024-01-15\",\"amount\":50,\"accountId\":1,\"categoryId\":2}",                       // null type
            "{\"date\":\"2024-01-15\",\"amount\":50,\"type\":\"EXPENSE\",\"categoryId\":2}",                  // null accountId
            "{\"date\":\"2024-01-15\",\"amount\":50,\"type\":\"EXPENSE\",\"accountId\":1}",                   // null categoryId
    })
    void create_invalidBody_returns400AndDoesNotSave(String body) {
        assertThat(mvc.post().uri("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void create_malformedJson_returns400() {
        assertThat(mvc.post().uri("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_unknownAccount_returns400ProblemDetailWithSpanishDetail() {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(mvc.post().uri("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail").isEqualTo("Cuenta no válida");
    }

    // ---------- PUT / DELETE: status codes ----------

    @Test
    void update_notFound_returns404ProblemDetail() {
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(mvc.put().uri("/api/transactions/{id}", 99)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Movimiento no encontrado");
    }

    @Test
    void delete_returns204AndDelegates() {
        assertThat(mvc.delete().uri("/api/transactions/{id}", 5)).hasStatus(HttpStatus.NO_CONTENT);
        verify(transactionRepository).deleteById(5L);
    }
}
