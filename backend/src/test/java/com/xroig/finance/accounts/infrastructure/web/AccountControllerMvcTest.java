package com.xroig.finance.accounts.infrastructure.web;

import com.xroig.finance.accounts.application.port.CreateAccount;
import com.xroig.finance.accounts.application.port.CreateAccount.CreateAccountCommand;
import com.xroig.finance.accounts.application.port.DeleteAccount;
import com.xroig.finance.accounts.application.port.FindAccounts;
import com.xroig.finance.accounts.application.port.UpdateAccount;
import com.xroig.finance.accounts.domain.Account;
import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.shared.domain.ConflictException;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HTTP contract for the migrated {@link AccountController} (stage H1). The slice
 * mocks the inbound ports — the controller never sees a repository — and checks the
 * same contract the legacy {@code controller.AccountControllerMvcTest} pinned:
 * routing, JSON, {@code @Valid} → 400, the domain advice (not found → 404, guard →
 * 409) and the legacy persistence advice ({@link DataIntegrityViolationException}
 * → 409 {@code problem+json}).
 */
@WebMvcTest(AccountController.class)
class AccountControllerMvcTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private FindAccounts findAccounts;
    @MockitoBean private CreateAccount createAccount;
    @MockitoBean private UpdateAccount updateAccount;
    @MockitoBean private DeleteAccount deleteAccount;

    private static final String VALID_BODY = """
            {"name":"Corriente","type":"CORRIENTE","initialBalance":100}
            """;

    private static Account account(Long id, String name, String amount) {
        return Account.rehydrate(new AccountId(id), name, "CORRIENTE", Money.of(amount));
    }

    @Test
    void findAll_returns200WithJsonArray() {
        when(findAccounts.all()).thenReturn(List.of(account(1L, "Corriente", "100")));

        assertThat(mvc.get().uri("/api/accounts"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson().extractingPath("$[0].name").isEqualTo("Corriente");
    }

    @Test
    void create_valid_returns201() {
        when(createAccount.create(any(CreateAccountCommand.class)))
                .thenReturn(account(7L, "Corriente", "100"));

        assertThat(mvc.post().uri("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.name").isEqualTo("Corriente");
    }

    @Test
    void create_blankName_returns400AndDoesNotCallUseCase() {
        assertThat(mvc.post().uri("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"","type":"CORRIENTE","initialBalance":0}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);

        verify(createAccount, never()).create(any());
    }

    @Test
    void update_valid_returns200WithUpdatedBody() {
        when(updateAccount.update(anyLong(), any()))
                .thenReturn(account(5L, "Nuevo", "250"));

        assertThat(mvc.put().uri("/api/accounts/{id}", 5)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Nuevo","type":"CORRIENTE","initialBalance":250}
                        """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.name").isEqualTo("Nuevo");
    }

    @Test
    void update_notFound_returns404() {
        when(updateAccount.update(anyLong(), any()))
                .thenThrow(new NotFoundException("Cuenta no encontrada"));

        assertThat(mvc.put().uri("/api/accounts/{id}", 5)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void delete_withGuard_returns409() {
        org.mockito.Mockito.doThrow(new ConflictException("La cuenta tiene movimientos asociados y no se puede eliminar"))
                .when(deleteAccount).delete(5L);

        assertThat(mvc.delete().uri("/api/accounts/{id}", 5))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void delete_happyPath_returns204() {
        assertThat(mvc.delete().uri("/api/accounts/{id}", 5))
                .hasStatus(HttpStatus.NO_CONTENT);
        verify(deleteAccount).delete(5L);
    }

    /** A persistence failure surfacing through the use case still becomes a 409 ProblemDetail (legacy advice). */
    @Test
    void create_dataIntegrityViolation_isMappedTo409ProblemDetail() {
        when(createAccount.create(any(CreateAccountCommand.class)))
                .thenThrow(new DataIntegrityViolationException("boom"));

        assertThat(mvc.post().uri("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("La operación viola una restricción de integridad de datos.");
    }
}
