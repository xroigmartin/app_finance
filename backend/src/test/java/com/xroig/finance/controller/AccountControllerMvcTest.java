package com.xroig.finance.controller;

import com.xroig.finance.model.Account;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.TransactionRepository;
import com.xroig.finance.repository.TransferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static com.xroig.finance.Fixtures.account;
import static com.xroig.finance.Fixtures.eur;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Level-3 reference test (stage M0): establishes the {@link WebMvcTest} slice
 * pattern with {@link MockitoBean} repositories and the AssertJ-based {@link
 * MockMvcTester}. It exercises the HTTP layer the E3 Mockito tests bypass —
 * routing, JSON (de)serialization, bean validation ({@code @Valid} → 400) and
 * the {@code @RestControllerAdvice} mapping ({@link DataIntegrityViolationException}
 * → 409 as {@code application/problem+json}) — on {@link AccountController}, the
 * simplest CRUD controller. The per-controller contracts live in M1..Mn.
 */
@WebMvcTest(AccountController.class)
class AccountControllerMvcTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private AccountRepository accountRepository;
    @MockitoBean private TransactionRepository transactionRepository;
    @MockitoBean private TransferRepository transferRepository;

    private static final String VALID_BODY = """
            {"name":"Corriente","type":"CORRIENTE","initialBalance":100}
            """;

    @Test
    void findAll_returns200WithJsonArray() {
        when(accountRepository.findAll()).thenReturn(List.of(account(1, "Corriente", eur("100"))));

        assertThat(mvc.get().uri("/api/accounts"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson().extractingPath("$[0].name").isEqualTo("Corriente");
    }

    @Test
    void create_valid_returns201AndClearedId() {
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(mvc.post().uri("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.name").isEqualTo("Corriente");
    }

    @Test
    void create_blankName_returns400AndDoesNotSave() {
        assertThat(mvc.post().uri("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"","type":"CORRIENTE","initialBalance":0}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void delete_withMovements_returns409() {
        when(transactionRepository.existsByAccountId(5L)).thenReturn(true);

        assertThat(mvc.delete().uri("/api/accounts/{id}", 5))
                .hasStatus(HttpStatus.CONFLICT);
        verify(accountRepository, never()).deleteById(any());
    }

    @Test
    void delete_happyPath_returns204() {
        when(transactionRepository.existsByAccountId(5L)).thenReturn(false);
        when(transferRepository.existsByFromAccountIdOrToAccountId(5L, 5L)).thenReturn(false);

        assertThat(mvc.delete().uri("/api/accounts/{id}", 5))
                .hasStatus(HttpStatus.NO_CONTENT);
        verify(accountRepository).deleteById(5L);
    }

    /** The slice must wire {@link GlobalExceptionHandler}: a persistence failure becomes a 409 ProblemDetail. */
    @Test
    void create_dataIntegrityViolation_isMappedTo409ProblemDetail() {
        when(accountRepository.save(any(Account.class)))
                .thenThrow(new DataIntegrityViolationException("boom"));

        assertThat(mvc.post().uri("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("La operación viola una restricción de integridad de datos.");
    }
}
