package com.xroig.finance.transfers.infrastructure.web;

import com.xroig.finance.imports.application.ImportResult;
import com.xroig.finance.imports.application.port.ImportTransfers;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
import com.xroig.finance.transfers.application.TransferView;
import com.xroig.finance.transfers.application.port.CreateTransfer;
import com.xroig.finance.transfers.application.port.CreateTransfer.TransferCommand;
import com.xroig.finance.transfers.application.port.DeleteTransfer;
import com.xroig.finance.transfers.application.port.FindTransfers;
import com.xroig.finance.transfers.application.port.UpdateTransfer;
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
 * HTTP-contract test for the migrated {@link TransferController} (stage H4). The branch
 * logic is verified by {@code TransferServiceTest}; here we pin what the {@code
 * @WebMvcTest} slice adds: query-parameter binding (default date window, a malformed
 * date → 400), bean validation on {@link TransferRequest}, the {@code @ResponseStatus}
 * codes, the {@link TransferView} JSON, and the domain exceptions as {@code problem+json}
 * (distinct-ends → 400, unknown account → 400, not found → 404).
 */
@WebMvcTest(TransferController.class)
class TransferControllerMvcTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private FindTransfers findTransfers;
    @MockitoBean private CreateTransfer createTransfer;
    @MockitoBean private UpdateTransfer updateTransfer;
    @MockitoBean private DeleteTransfer deleteTransfer;
    @MockitoBean private ImportTransfers importTransfers;

    private static final String VALID_BODY = """
            {"date":"2024-01-15","amount":100,"fromAccountId":1,"toAccountId":2}
            """;

    private static TransferView view(String amount) {
        return new TransferView(7L, LocalDate.of(2024, 1, 15), new BigDecimal(amount), "x", null, null);
    }

    // ---------- GET: query-parameter binding ----------

    @Test
    void find_bindsParams_andReturnsJson() {
        when(findTransfers.search(any(), any(), eq(1L))).thenReturn(List.of(view("100")));

        assertThat(mvc.get().uri("/api/transfers?from=2024-01-01&to=2024-01-31&accountId=1"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[0].amount").asNumber().isEqualTo(100);

        verify(findTransfers).search(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), 1L);
    }

    @Test
    void find_withoutParams_appliesWideDefaultDateWindow() {
        when(findTransfers.search(any(), any(), isNull())).thenReturn(List.of());

        assertThat(mvc.get().uri("/api/transfers")).hasStatusOk();

        verify(findTransfers).search(
                eq(LocalDate.of(1970, 1, 1)), eq(LocalDate.of(2999, 12, 31)), isNull());
    }

    @Test
    void find_withMalformedDate_returns400() {
        assertThat(mvc.get().uri("/api/transfers?from=nope")).hasStatus(HttpStatus.BAD_REQUEST);
        verify(findTransfers, never()).search(any(), any(), any());
    }

    // ---------- POST: happy path and bean validation ----------

    @Test
    void create_valid_returns201WithJson() {
        when(createTransfer.create(any(TransferCommand.class))).thenReturn(view("100"));

        assertThat(mvc.post().uri("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.amount").asNumber().isEqualTo(100);
    }

    @ParameterizedTest(name = "invalid TransferRequest → 400")
    @ValueSource(strings = {
            "{\"amount\":100,\"fromAccountId\":1,\"toAccountId\":2}",                      // null date
            "{\"date\":\"2024-01-15\",\"amount\":0,\"fromAccountId\":1,\"toAccountId\":2}", // @Positive (0)
            "{\"date\":\"2024-01-15\",\"amount\":-5,\"fromAccountId\":1,\"toAccountId\":2}",// @Positive (negative)
            "{\"date\":\"2024-01-15\",\"amount\":100,\"toAccountId\":2}",                   // null fromAccountId
            "{\"date\":\"2024-01-15\",\"amount\":100,\"fromAccountId\":1}",                 // null toAccountId
    })
    void create_invalidBody_returns400AndDoesNotCreate(String body) {
        assertThat(mvc.post().uri("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verify(createTransfer, never()).create(any());
    }

    @Test
    void create_malformedJson_returns400() {
        assertThat(mvc.post().uri("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_sameSourceAndTarget_returns400ProblemDetail() {
        when(createTransfer.create(any(TransferCommand.class)))
                .thenThrow(new ValidationException("La cuenta de origen y la de destino deben ser distintas"));

        assertThat(mvc.post().uri("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"date":"2024-01-15","amount":100,"fromAccountId":1,"toAccountId":1}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("La cuenta de origen y la de destino deben ser distintas");
    }

    @Test
    void create_unknownAccount_returns400ProblemDetail() {
        when(createTransfer.create(any(TransferCommand.class)))
                .thenThrow(new ValidationException("Cuenta no válida"));

        assertThat(mvc.post().uri("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").isEqualTo("Cuenta no válida");
    }

    // ---------- PUT / DELETE / import ----------

    @Test
    void update_notFound_returns404ProblemDetail() {
        when(updateTransfer.update(anyLong(), any()))
                .thenThrow(new NotFoundException("Transferencia no encontrada"));

        assertThat(mvc.put().uri("/api/transfers/{id}", 99)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Transferencia no encontrada");
    }

    @Test
    void update_valid_returns200WithBody() {
        when(updateTransfer.update(anyLong(), any())).thenReturn(view("250"));

        assertThat(mvc.put().uri("/api/transfers/{id}", 5)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatusOk()
                .bodyJson().extractingPath("$.amount").asNumber().isEqualTo(250);
    }

    @Test
    void importFile_delegatesToImportUseCase() {
        when(importTransfers.importTransfers(any()))
                .thenReturn(new ImportResult(3, 0, List.of()));

        assertThat(mvc.post().uri("/api/transfers/import")
                .multipart()
                .file(new org.springframework.mock.web.MockMultipartFile(
                        "file", "x.csv", "text/csv", "data".getBytes())))
                .hasStatusOk()
                .bodyJson().extractingPath("$.imported").asNumber().isEqualTo(3);
    }

    @Test
    void delete_returns204AndDelegates() {
        assertThat(mvc.delete().uri("/api/transfers/{id}", 5)).hasStatus(HttpStatus.NO_CONTENT);
        verify(deleteTransfer).delete(5L);
    }
}
