package com.xroig.finance.accounts.application;

import com.xroig.finance.accounts.application.port.CreateAccount.CreateAccountCommand;
import com.xroig.finance.accounts.application.port.UpdateAccount.UpdateAccountCommand;
import com.xroig.finance.accounts.domain.Account;
import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.accounts.domain.AccountRepository;
import com.xroig.finance.accounts.domain.AccountUsage;
import com.xroig.finance.shared.domain.ConflictException;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application-service tests for the accounts use cases, mocking the outbound ports
 * ({@link AccountRepository}, {@link AccountUsage}). They cover the orchestration
 * the controller used to do: id resolution, the not-found mapping and the deletion
 * guard — the invariants themselves are tested in {@code AccountTest}.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accounts;
    @Mock private AccountUsage usage;

    private AccountService service() {
        return new AccountService(accounts, usage);
    }

    @Test
    void all_delegatesToTheRepository() {
        Account stored = Account.rehydrate(new AccountId(1L), "Corriente", "Banco", Money.of("100"));
        when(accounts.findAll()).thenReturn(java.util.List.of(stored));

        assertThat(service().all()).containsExactly(stored);
    }

    @Test
    void create_savesANewAggregate() {
        when(accounts.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        Account created = service().create(new CreateAccountCommand("Corriente", "Banco", Money.of("100")));

        assertThat(created.id()).isNull();
        assertThat(created.name()).isEqualTo("Corriente");
        assertThat(created.initialBalance()).isEqualTo(Money.of("100"));
        verify(accounts).save(any(Account.class));
    }

    @Test
    void update_notFoundThrows() {
        when(accounts.findById(new AccountId(5L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(5L, new UpdateAccountCommand("X", "Banco", Money.zero())))
                .isInstanceOf(NotFoundException.class);
        verify(accounts, never()).save(any());
    }

    @Test
    void update_copiesEditableFields() {
        Account existing = Account.rehydrate(new AccountId(5L), "Viejo", "Banco", Money.zero());
        when(accounts.findById(new AccountId(5L))).thenReturn(Optional.of(existing));
        when(accounts.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        Account updated = service().update(5L, new UpdateAccountCommand("Nuevo", "AHORRO", Money.of("250")));

        assertThat(updated.name()).isEqualTo("Nuevo");
        assertThat(updated.type()).isEqualTo("AHORRO");
        assertThat(updated.initialBalance()).isEqualTo(Money.of("250"));
    }

    @Test
    void delete_withMovementsThrowsConflict() {
        when(usage.hasMovements(new AccountId(5L))).thenReturn(true);

        assertThatThrownBy(() -> service().delete(5L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("movimientos");
        verify(accounts, never()).deleteById(any());
    }

    @Test
    void delete_withTransfersThrowsConflict() {
        when(usage.hasMovements(new AccountId(5L))).thenReturn(false);
        when(usage.hasTransfers(new AccountId(5L))).thenReturn(true);

        assertThatThrownBy(() -> service().delete(5L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("transferencias");
        verify(accounts, never()).deleteById(any());
    }

    @Test
    void delete_happyPathDeletesById() {
        when(usage.hasMovements(new AccountId(5L))).thenReturn(false);
        when(usage.hasTransfers(new AccountId(5L))).thenReturn(false);

        service().delete(5L);

        verify(accounts).deleteById(new AccountId(5L));
    }
}
