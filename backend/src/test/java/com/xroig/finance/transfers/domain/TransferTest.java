package com.xroig.finance.transfers.domain;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure domain tests for the {@link Transfer} aggregate: the distinct-ends invariant
 * that used to live in {@code TransferController.apply}, plus the basic field
 * invariants (positive amount, date required) and the {@code reassign} edit path.
 */
class TransferTest {

    private static final LocalDate D = LocalDate.of(2024, 3, 10);
    private static final AccountId FROM = new AccountId(1L);
    private static final AccountId TO = new AccountId(2L);

    @Test
    void create_buildsATransferWithoutIdentity() {
        Transfer t = Transfer.create(D, Money.of("100"), "Traspaso", FROM, TO);

        assertThat(t.id()).isNull();
        assertThat(t.amount()).isEqualTo(Money.of("100"));
        assertThat(t.date()).isEqualTo(D);
        assertThat(t.fromAccountId()).isEqualTo(FROM);
        assertThat(t.toAccountId()).isEqualTo(TO);
        assertThat(t.description()).isEqualTo("Traspaso");
    }

    @Test
    void create_rejectsSameSourceAndDestination() {
        assertThatThrownBy(() -> Transfer.create(D, Money.of("100"), null, FROM, FROM))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("origen y la de destino deben ser distintas");
    }

    @Test
    void create_rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> Transfer.create(D, Money.zero(), null, FROM, TO))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("positivo");
        assertThatThrownBy(() -> Transfer.create(D, Money.of("-5"), null, FROM, TO))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_rejectsNullAmount() {
        assertThatThrownBy(() -> Transfer.create(D, null, null, FROM, TO))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("positivo");
    }

    @Test
    void create_rejectsNullDate() {
        assertThatThrownBy(() -> Transfer.create(null, Money.of("5"), null, FROM, TO))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("fecha");
    }

    @Test
    void reassign_reappliesFieldsAndRechecksInvariants() {
        Transfer t = Transfer.rehydrate(new TransferId(5L), D, Money.of("10"), "x", FROM, TO);

        t.reassign(LocalDate.of(2024, 4, 1), Money.of("250"), "Editado", TO, FROM);

        assertThat(t.amount()).isEqualTo(Money.of("250"));
        assertThat(t.date()).isEqualTo(LocalDate.of(2024, 4, 1));
        assertThat(t.fromAccountId()).isEqualTo(TO);
        assertThat(t.toAccountId()).isEqualTo(FROM);
        assertThat(t.description()).isEqualTo("Editado");
    }

    @Test
    void reassign_rejectsSameSourceAndDestination() {
        Transfer t = Transfer.rehydrate(new TransferId(5L), D, Money.of("10"), null, FROM, TO);

        assertThatThrownBy(() -> t.reassign(D, Money.of("10"), null, FROM, FROM))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("origen y la de destino deben ser distintas");
    }

    @Test
    void reassign_rejectsNonPositiveAmount() {
        Transfer t = Transfer.rehydrate(new TransferId(5L), D, Money.of("10"), null, FROM, TO);

        assertThatThrownBy(() -> t.reassign(D, Money.of("-1"), null, FROM, TO))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("positivo");
    }

    @Test
    void rehydrate_requiresIdentity() {
        assertThatThrownBy(() -> Transfer.rehydrate(null, D, Money.of("5"), null, FROM, TO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transferId_rejectsNull() {
        assertThatThrownBy(() -> new TransferId(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
