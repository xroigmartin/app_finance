package com.xroig.finance.controller;

import com.xroig.finance.model.Transfer;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.TransferRepository;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Level-3 HTTP-contract test (stage M4) for {@link TransferController}. The branch
 * logic is in the Mockito test {@code TransferControllerTest}; here we pin what the
 * {@code @WebMvcTest} slice adds: query-param binding with the default date window
 * (and a malformed date → 400), the {@code TransferRequest} validations
 * ({@code @NotNull}/{@code @Positive} → 400), the {@code @ResponseStatus} codes, and
 * the {@code ResponseStatusException} → {@code application/problem+json} mappings
 * (same source/target account → 400, unknown account → 400, not found → 404).
 */
@WebMvcTest(TransferController.class)
class TransferControllerMvcTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private TransferRepository transferRepository;
    @MockitoBean private AccountRepository accountRepository;
    @MockitoBean private ImportService importService;

    private static final String VALID_BODY = """
            {"date":"2024-01-15","amount":100,"fromAccountId":1,"toAccountId":2}
            """;

    // ---------- GET: query-parameter binding ----------

    @Test
    void find_bindsParams() {
        when(transferRepository.search(any(), any(), eq(1L))).thenReturn(List.of());

        assertThat(mvc.get().uri("/api/transfers?from=2024-01-01&to=2024-01-31&accountId=1")).hasStatusOk();
        verify(transferRepository).search(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), 1L);
    }

    @Test
    void find_withoutParams_appliesWideDefaultDateWindow() {
        when(transferRepository.search(any(), any(), isNull())).thenReturn(List.of());

        assertThat(mvc.get().uri("/api/transfers")).hasStatusOk();

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(transferRepository).search(from.capture(), to.capture(), isNull());
        assertThat(from.getValue()).isEqualTo(LocalDate.of(1970, 1, 1));
        assertThat(to.getValue()).isEqualTo(LocalDate.of(2999, 12, 31));
    }

    @Test
    void find_withMalformedDate_returns400() {
        assertThat(mvc.get().uri("/api/transfers?from=nope")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    // ---------- POST: happy path and validation ----------

    @Test
    void create_valid_returns201() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(1, "Corriente")));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(2, "Ahorro")));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(mvc.post().uri("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.amount").asNumber().isEqualTo(100);
    }

    @ParameterizedTest(name = "invalid TransferRequest → 400: {0}")
    @ValueSource(strings = {
            "{\"amount\":100,\"fromAccountId\":1,\"toAccountId\":2}",                      // null date
            "{\"date\":\"2024-01-15\",\"amount\":0,\"fromAccountId\":1,\"toAccountId\":2}", // @Positive (0)
            "{\"date\":\"2024-01-15\",\"amount\":-5,\"fromAccountId\":1,\"toAccountId\":2}",// @Positive (negative)
            "{\"date\":\"2024-01-15\",\"amount\":100,\"toAccountId\":2}",                   // null fromAccountId
            "{\"date\":\"2024-01-15\",\"amount\":100,\"fromAccountId\":1}",                 // null toAccountId
    })
    void create_invalidBody_returns400AndDoesNotSave(String body) {
        assertThat(mvc.post().uri("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verify(transferRepository, never()).save(any());
    }

    @Test
    void create_malformedJson_returns400() {
        assertThat(mvc.post().uri("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_sameSourceAndTarget_returns400ProblemDetail() {
        assertThat(mvc.post().uri("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"date":"2024-01-15","amount":100,"fromAccountId":1,"toAccountId":1}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("La cuenta de origen y la de destino deben ser distintas");
        verify(transferRepository, never()).save(any());
    }

    @Test
    void create_unknownAccount_returns400ProblemDetail() {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(mvc.post().uri("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").isEqualTo("Cuenta no válida");
    }

    // ---------- PUT / DELETE ----------

    @Test
    void update_notFound_returns404ProblemDetail() {
        when(transferRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(mvc.put().uri("/api/transfers/{id}", 99)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Transferencia no encontrada");
    }

    @Test
    void delete_returns204AndDelegates() {
        assertThat(mvc.delete().uri("/api/transfers/{id}", 5)).hasStatus(HttpStatus.NO_CONTENT);
        verify(transferRepository).deleteById(5L);
    }
}
