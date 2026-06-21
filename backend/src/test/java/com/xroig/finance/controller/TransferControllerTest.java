package com.xroig.finance.controller;

import com.xroig.finance.controller.TransferController.TransferRequest;
import com.xroig.finance.model.Account;
import com.xroig.finance.model.Transfer;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.TransferRepository;
import com.xroig.finance.service.ImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static com.xroig.finance.Fixtures.account;
import static com.xroig.finance.Fixtures.eur;
import static com.xroig.finance.Fixtures.transfer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TransferController} with mocked repositories (stage E3d):
 * account resolution, the same-account guard, update and the find defaults.
 */
@ExtendWith(MockitoExtension.class)
class TransferControllerTest {

    @Mock private TransferRepository transferRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ImportService importService;
    @InjectMocks private TransferController controller;

    private final Account corriente = account(1, "Corriente");
    private final Account ahorro = account(2, "Ahorro");
    private final LocalDate d = LocalDate.of(2024, 3, 10);

    private TransferRequest req(Long from, Long to) {
        return new TransferRequest(d, eur("100"), "Traspaso", from, to);
    }

    private static HttpStatus statusOf(Throwable t) {
        return (HttpStatus) ((ResponseStatusException) t).getStatusCode();
    }

    @Test
    void create_resolvesBothAccounts() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(corriente));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(ahorro));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(i -> i.getArgument(0));

        Transfer saved = controller.create(req(1L, 2L));

        assertThat(saved.getFromAccount()).isSameAs(corriente);
        assertThat(saved.getToAccount()).isSameAs(ahorro);
        assertThat(saved.getAmount()).isEqualByComparingTo("100");
        assertThat(saved.getDate()).isEqualTo(d);
    }

    @Test
    void create_sameAccountThrows400() {
        assertThatThrownBy(() -> controller.create(req(1L, 1L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_invalidFromAccountThrows400() {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.create(req(1L, 2L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void update_notFoundThrows404() {
        when(transferRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.update(5L, req(1L, 2L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void update_appliesAndSaves() {
        Transfer existing = transfer(5L, eur("10"), ahorro, corriente, d);
        when(transferRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(corriente));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(ahorro));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(i -> i.getArgument(0));

        Transfer updated = controller.update(5L, req(1L, 2L));

        assertThat(updated.getAmount()).isEqualByComparingTo("100");
        assertThat(updated.getFromAccount()).isSameAs(corriente);
        assertThat(updated.getToAccount()).isSameAs(ahorro);
    }

    @Test
    void delete_delegatesToRepository() {
        controller.delete(5L);
        verify(transferRepository).deleteById(5L);
    }

    @Test
    void find_usesWideDefaultsWhenNoDatesGiven() {
        controller.find(null, null, null);
        verify(transferRepository).search(
                LocalDate.of(1970, 1, 1), LocalDate.of(2999, 12, 31), null);
    }
}
