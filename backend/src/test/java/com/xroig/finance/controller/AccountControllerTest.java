package com.xroig.finance.controller;

import com.xroig.finance.model.Account;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.TransactionRepository;
import com.xroig.finance.repository.TransferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static com.xroig.finance.Fixtures.account;
import static com.xroig.finance.Fixtures.eur;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AccountController} with mocked repositories (stage E3d):
 * create/update and the deletion guards (movements / transfers).
 */
@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private TransferRepository transferRepository;
    @InjectMocks private AccountController controller;

    private static HttpStatus statusOf(Throwable t) {
        return (HttpStatus) ((ResponseStatusException) t).getStatusCode();
    }

    @Test
    void create_clearsIdAndSaves() {
        Account body = account(99, "Corriente", eur("100"));
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        Account saved = controller.create(body);

        assertThat(saved.getId()).isNull();
        assertThat(saved.getName()).isEqualTo("Corriente");
        assertThat(saved.getInitialBalance()).isEqualByComparingTo("100");
    }

    @Test
    void update_notFoundThrows404() {
        when(accountRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.update(5L, account(5, "X")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void update_copiesEditableFields() {
        Account existing = account(5, "Viejo", eur("0"));
        Account body = account(5, "Nuevo", eur("250"));
        body.setType("AHORRO");
        when(accountRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        Account updated = controller.update(5L, body);

        assertThat(updated.getName()).isEqualTo("Nuevo");
        assertThat(updated.getType()).isEqualTo("AHORRO");
        assertThat(updated.getInitialBalance()).isEqualByComparingTo("250");
    }

    @Test
    void delete_withTransactionsThrows409() {
        when(transactionRepository.existsByAccountId(5L)).thenReturn(true);

        assertThatThrownBy(() -> controller.delete(5L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
        verify(accountRepository, never()).deleteById(any());
    }

    @Test
    void delete_withTransfersThrows409() {
        when(transactionRepository.existsByAccountId(5L)).thenReturn(false);
        when(transferRepository.existsByFromAccountIdOrToAccountId(5L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> controller.delete(5L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void delete_happyPathDeletesById() {
        when(transactionRepository.existsByAccountId(5L)).thenReturn(false);
        when(transferRepository.existsByFromAccountIdOrToAccountId(5L, 5L)).thenReturn(false);

        controller.delete(5L);

        verify(accountRepository).deleteById(5L);
    }
}
