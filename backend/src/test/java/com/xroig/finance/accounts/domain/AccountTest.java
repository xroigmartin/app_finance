package com.xroig.finance.accounts.domain;

import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure domain tests for the {@link Account} aggregate (no Spring, no Mockito): the
 * invariants the legacy entity only declared via bean-validation annotations, plus
 * the computed-balance concept that used to live in the dashboard query.
 */
class AccountTest {

    @Test
    void create_buildsAValidAccount() {
        Account account = Account.create("Corriente", "Banco", Money.of("100"));

        assertThat(account.id()).isNull();
        assertThat(account.name()).isEqualTo("Corriente");
        assertThat(account.type()).isEqualTo("Banco");
        assertThat(account.initialBalance()).isEqualTo(Money.of("100"));
    }

    @Test
    void create_rejectsBlankName() {
        assertThatThrownBy(() -> Account.create("  ", "Banco", Money.zero()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("nombre");
    }

    @Test
    void create_rejectsNullName() {
        assertThatThrownBy(() -> Account.create(null, "Banco", Money.zero()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("nombre");
    }

    @Test
    void create_rejectsBlankType() {
        assertThatThrownBy(() -> Account.create("Corriente", "", Money.zero()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("tipo");
    }

    @Test
    void create_rejectsNullInitialBalance() {
        assertThatThrownBy(() -> Account.create("Corriente", "Banco", null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("saldo inicial");
    }

    @Test
    void rehydrate_requiresIdentity() {
        assertThatThrownBy(() -> Account.rehydrate(null, "Corriente", "Banco", Money.zero()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_replacesEditableFieldsAndRevalidates() {
        Account account = Account.rehydrate(new AccountId(5L), "Viejo", "Banco", Money.zero());

        account.update("Nuevo", "AHORRO", Money.of("250"));

        assertThat(account.id()).isEqualTo(new AccountId(5L));
        assertThat(account.name()).isEqualTo("Nuevo");
        assertThat(account.type()).isEqualTo("AHORRO");
        assertThat(account.initialBalance()).isEqualTo(Money.of("250"));
    }

    @Test
    void update_rejectsBlankName() {
        Account account = Account.rehydrate(new AccountId(5L), "Viejo", "Banco", Money.zero());

        assertThatThrownBy(() -> account.update(" ", "AHORRO", Money.of("250")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void accountId_rejectsNullValue() {
        assertThatThrownBy(() -> new AccountId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void balanceWith_isInitialPlusNetMovements() {
        Account account = Account.create("Corriente", "Banco", Money.of("100"));

        assertThat(account.balanceWith(Money.of("-30"))).isEqualTo(Money.of("70"));
        assertThat(account.balanceWith(Money.zero())).isEqualTo(Money.of("100"));
    }
}
