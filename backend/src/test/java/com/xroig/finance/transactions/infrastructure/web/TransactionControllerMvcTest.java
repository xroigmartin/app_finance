package com.xroig.finance.transactions.infrastructure.web;

import com.xroig.finance.imports.application.ImportResult;
import com.xroig.finance.imports.application.port.ImportTransactions;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.shared.domain.ValidationException;
import com.xroig.finance.transactions.application.TransactionView;
import com.xroig.finance.transactions.application.port.CreateTransaction;
import com.xroig.finance.transactions.application.port.CreateTransaction.TransactionCommand;
import com.xroig.finance.transactions.application.port.DeleteTransaction;
import com.xroig.finance.transactions.application.port.FindTransactions;
import com.xroig.finance.transactions.application.port.UpdateTransaction;
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
 * HTTP-contract test for the migrated {@link TransactionController} (stage H3). The
 * branch logic is verified by {@code TransactionServiceTest}; here we pin what the
 * {@code @WebMvcTest} slice adds: query-parameter binding (default date window, a
 * malformed date → 400), bean validation on {@link TransactionRequest}, the {@code
 * @ResponseStatus} codes, the {@link TransactionView} JSON, and the domain exceptions
 * as {@code problem+json}.
 */
@WebMvcTest(TransactionController.class)
class TransactionControllerMvcTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private FindTransactions findTransactions;
    @MockitoBean private CreateTransaction createTransaction;
    @MockitoBean private UpdateTransaction updateTransaction;
    @MockitoBean private DeleteTransaction deleteTransaction;
    @MockitoBean private ImportTransactions importTransactions;

    private static final String VALID_BODY = """
            {"date":"2024-01-15","amount":50,"type":"EXPENSE","accountId":1,"categoryId":2}
            """;

    private static TransactionView view(String amount, TransactionType type) {
        return new TransactionView(7L, LocalDate.of(2024, 1, 15), new BigDecimal(amount), "x", type,
                null, null, null);
    }

    // ---------- GET: query-parameter binding ----------

    @Test
    void find_bindsAllFilters_andReturnsJson() {
        when(findTransactions.search(any(), any(), any(), any())).thenReturn(List.of(view("50", TransactionType.EXPENSE)));

        assertThat(mvc.get().uri("/api/transactions?from=2024-01-01&to=2024-01-31&accountId=1&categoryId=2"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[0].amount").asNumber().isEqualTo(50);

        verify(findTransactions).search(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), 1L, 2L);
    }

    @Test
    void find_withoutParams_appliesWideDefaultDateWindow() {
        when(findTransactions.search(any(), any(), isNull(), isNull())).thenReturn(List.of());

        assertThat(mvc.get().uri("/api/transactions")).hasStatusOk();

        verify(findTransactions).search(
                eq(LocalDate.of(1970, 1, 1)), eq(LocalDate.of(2999, 12, 31)), isNull(), isNull());
    }

    @Test
    void find_withMalformedDate_returns400() {
        assertThat(mvc.get().uri("/api/transactions?from=no-es-fecha")).hasStatus(HttpStatus.BAD_REQUEST);
        verify(findTransactions, never()).search(any(), any(), any(), any());
    }

    @Test
    void recent_returns200WithJsonArray() {
        when(findTransactions.recent()).thenReturn(List.of(view("50", TransactionType.EXPENSE)));

        assertThat(mvc.get().uri("/api/transactions/recent"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.length()").asNumber().isEqualTo(1);
    }

    // ---------- POST: happy path and bean validation ----------

    @Test
    void create_valid_returns201WithJson() {
        when(createTransaction.create(any(TransactionCommand.class)))
                .thenReturn(view("50", TransactionType.EXPENSE));

        assertThat(mvc.post().uri("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.type").isEqualTo("EXPENSE");
    }

    @ParameterizedTest(name = "invalid body → 400")
    @ValueSource(strings = {
            "{\"date\":\"2024-01-15\",\"amount\":-5,\"type\":\"EXPENSE\",\"accountId\":1,\"categoryId\":2}",
            "{\"date\":\"2024-01-15\",\"amount\":0,\"type\":\"EXPENSE\",\"accountId\":1,\"categoryId\":2}",
            "{\"amount\":50,\"type\":\"EXPENSE\",\"accountId\":1,\"categoryId\":2}",
            "{\"date\":\"2024-01-15\",\"type\":\"EXPENSE\",\"accountId\":1,\"categoryId\":2}",
            "{\"date\":\"2024-01-15\",\"amount\":50,\"accountId\":1,\"categoryId\":2}",
            "{\"date\":\"2024-01-15\",\"amount\":50,\"type\":\"EXPENSE\",\"categoryId\":2}",
            "{\"date\":\"2024-01-15\",\"amount\":50,\"type\":\"EXPENSE\",\"accountId\":1}",
    })
    void create_invalidBody_returns400AndDoesNotCreate(String body) {
        assertThat(mvc.post().uri("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verify(createTransaction, never()).create(any());
    }

    @Test
    void create_malformedJson_returns400() {
        assertThat(mvc.post().uri("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_unknownAccount_returns400ProblemDetailWithSpanishDetail() {
        when(createTransaction.create(any(TransactionCommand.class)))
                .thenThrow(new ValidationException("Cuenta no válida"));

        assertThat(mvc.post().uri("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail").isEqualTo("Cuenta no válida");
    }

    // ---------- PUT / DELETE ----------

    @Test
    void update_notFound_returns404ProblemDetail() {
        when(updateTransaction.update(anyLong(), any()))
                .thenThrow(new NotFoundException("Movimiento no encontrado"));

        assertThat(mvc.put().uri("/api/transactions/{id}", 99)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Movimiento no encontrado");
    }

    @Test
    void update_valid_returns200WithBody() {
        when(updateTransaction.update(anyLong(), any())).thenReturn(view("50", TransactionType.INCOME));

        assertThat(mvc.put().uri("/api/transactions/{id}", 5)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatusOk()
                .bodyJson().extractingPath("$.type").isEqualTo("INCOME");
    }

    @Test
    void importFile_delegatesToImportUseCase() {
        when(importTransactions.importTransactions(any(), eq(1L)))
                .thenReturn(new ImportResult(3, 0, List.of()));

        assertThat(mvc.post().uri("/api/transactions/import")
                .param("accountId", "1")
                .multipart()
                .file(new org.springframework.mock.web.MockMultipartFile(
                        "file", "x.csv", "text/csv", "data".getBytes())))
                .hasStatusOk()
                .bodyJson().extractingPath("$.imported").asNumber().isEqualTo(3);
    }

    @Test
    void delete_returns204AndDelegates() {
        assertThat(mvc.delete().uri("/api/transactions/{id}", 5)).hasStatus(HttpStatus.NO_CONTENT);
        verify(deleteTransaction).delete(5L);
    }
}
