package com.xroig.finance.transfers.application;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
import com.xroig.finance.transfers.application.port.CreateTransfer.TransferCommand;
import com.xroig.finance.transfers.domain.AccountExistence;
import com.xroig.finance.transfers.domain.Transfer;
import com.xroig.finance.transfers.domain.TransferId;
import com.xroig.finance.transfers.domain.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application-service tests for the transfers use cases with the outbound ports mocked.
 * They reproduce the branch logic the legacy {@code TransferControllerTest} pinned
 * (the distinct-ends guard fires before account existence, account resolution, update
 * not-found and the delete delegation) — the distinct-ends invariant itself lives in
 * {@code TransferTest}.
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final LocalDate D = LocalDate.of(2024, 3, 10);
    private static final AccountId FROM = new AccountId(1L);
    private static final AccountId TO = new AccountId(2L);
    private static final TransferView ANY_VIEW = new TransferView(100L, D, BigDecimal.TEN, "x", null, null);

    @Mock private TransferRepository transfers;
    @Mock private AccountExistence accounts;
    @Mock private TransferQueryPort queries;

    private TransferService service;

    @BeforeEach
    void setUp() {
        service = new TransferService(transfers, accounts, queries);
    }

    private TransferCommand command(Long from, Long to) {
        return new TransferCommand(D, new BigDecimal("100"), "Traspaso", from, to);
    }

    private void stubSaveAndView() {
        when(transfers.save(any())).thenAnswer(i -> {
            Transfer t = i.getArgument(0);
            return Transfer.rehydrate(new TransferId(100L), t.date(), t.amount(), t.description(),
                    t.fromAccountId(), t.toAccountId());
        });
        when(queries.findById(new TransferId(100L))).thenReturn(Optional.of(ANY_VIEW));
    }

    private Transfer captureSaved() {
        ArgumentCaptor<Transfer> captor = ArgumentCaptor.forClass(Transfer.class);
        verify(transfers).save(captor.capture());
        return captor.getValue();
    }

    // ---------- reads ----------

    @Test
    void search_delegatesToQueryPort() {
        when(queries.search(D, D, 1L)).thenReturn(List.of(ANY_VIEW));
        assertThat(service.search(D, D, 1L)).containsExactly(ANY_VIEW);
    }

    // ---------- create ----------

    @Test
    void create_resolvesBothAccountsAndReturnsView() {
        when(accounts.exists(FROM)).thenReturn(true);
        when(accounts.exists(TO)).thenReturn(true);
        stubSaveAndView();

        assertThat(service.create(command(1L, 2L))).isEqualTo(ANY_VIEW);

        Transfer saved = captureSaved();
        assertThat(saved.fromAccountId()).isEqualTo(FROM);
        assertThat(saved.toAccountId()).isEqualTo(TO);
        assertThat(saved.amount()).isEqualTo(Money.of("100"));
    }

    @Test
    void create_sameAccountThrowsBeforeCheckingExistence() {
        assertThatThrownBy(() -> service.create(command(1L, 1L)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("origen y la de destino deben ser distintas");
        verify(accounts, never()).exists(any());
        verify(transfers, never()).save(any());
    }

    @Test
    void create_invalidFromAccountThrows() {
        when(accounts.exists(FROM)).thenReturn(false);

        assertThatThrownBy(() -> service.create(command(1L, 2L)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Cuenta no válida");
        verify(transfers, never()).save(any());
    }

    @Test
    void create_invalidToAccountThrows() {
        when(accounts.exists(FROM)).thenReturn(true);
        when(accounts.exists(TO)).thenReturn(false);

        assertThatThrownBy(() -> service.create(command(1L, 2L)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Cuenta no válida");
        verify(transfers, never()).save(any());
    }

    @Test
    void create_whenSavedViewCannotBeRead_failsLoudly() {
        when(accounts.exists(FROM)).thenReturn(true);
        when(accounts.exists(TO)).thenReturn(true);
        when(transfers.save(any())).thenAnswer(i -> {
            Transfer t = i.getArgument(0);
            return Transfer.rehydrate(new TransferId(100L), t.date(), t.amount(), t.description(),
                    t.fromAccountId(), t.toAccountId());
        });
        when(queries.findById(new TransferId(100L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command(1L, 2L)))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- update ----------

    @Test
    void update_notFoundThrows() {
        when(transfers.findById(new TransferId(5L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(5L, command(1L, 2L)))
                .isInstanceOf(NotFoundException.class);
        verify(transfers, never()).save(any());
    }

    @Test
    void update_appliesAndSaves() {
        Transfer existing = Transfer.rehydrate(new TransferId(5L), D, Money.of("10"), null, TO, FROM);
        when(transfers.findById(new TransferId(5L))).thenReturn(Optional.of(existing));
        when(accounts.exists(FROM)).thenReturn(true);
        when(accounts.exists(TO)).thenReturn(true);
        stubSaveAndView();

        service.update(5L, command(1L, 2L));

        Transfer saved = captureSaved();
        assertThat(saved.amount()).isEqualTo(Money.of("100"));
        assertThat(saved.fromAccountId()).isEqualTo(FROM);
        assertThat(saved.toAccountId()).isEqualTo(TO);
    }

    @Test
    void update_sameAccountThrows() {
        Transfer existing = Transfer.rehydrate(new TransferId(5L), D, Money.of("10"), null, FROM, TO);
        when(transfers.findById(new TransferId(5L))).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(5L, command(1L, 1L)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("origen y la de destino deben ser distintas");
        verify(transfers, never()).save(any());
    }

    // ---------- delete ----------

    @Test
    void delete_delegatesToRepository() {
        service.delete(5L);
        verify(transfers).deleteById(new TransferId(5L));
    }
}
