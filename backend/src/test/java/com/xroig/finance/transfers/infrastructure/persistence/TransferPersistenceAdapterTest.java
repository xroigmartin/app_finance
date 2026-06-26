package com.xroig.finance.transfers.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.accounts.domain.Account;
import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaMapper;
import com.xroig.finance.accounts.infrastructure.persistence.AccountPersistenceAdapter;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.transfers.application.TransferView;
import com.xroig.finance.transfers.domain.Transfer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapter test (Level 2) against real PostgreSQL: the {@link TransferPersistenceAdapter}
 * round-trip through {@link TransferJpaMapper} (the pure aggregate survives, both ends
 * referenced by id) and the read-side {@link TransferQueryAdapter} assembling the nested
 * {@link TransferView} — including the {@code search} window/account filter (matching a
 * transfer on either side) ordered newest first.
 */
@Import({TransferPersistenceAdapter.class, TransferQueryAdapter.class, TransferJpaMapper.class,
        AccountPersistenceAdapter.class, AccountJpaMapper.class})
class TransferPersistenceAdapterTest extends PostgresTestBase {

    private static final LocalDate JAN = LocalDate.of(2024, 1, 10);
    private static final LocalDate FEB = LocalDate.of(2024, 2, 10);
    private static final LocalDate MAR = LocalDate.of(2024, 3, 10);
    private static final LocalDate WIDE_FROM = LocalDate.of(1970, 1, 1);
    private static final LocalDate WIDE_TO = LocalDate.of(2999, 12, 31);

    @Autowired private TransferPersistenceAdapter adapter;
    @Autowired private TransferQueryAdapter queries;
    @Autowired private AccountPersistenceAdapter accounts;

    private AccountId corriente;
    private AccountId ahorro;

    private void seedAccounts() {
        corriente = accounts.save(Account.create("Corriente", "Banco", Money.of("100"))).id();
        ahorro = accounts.save(Account.create("Ahorro", "Banco", Money.zero())).id();
    }

    private Transfer save(AccountId from, AccountId to, String amount, LocalDate date) {
        return adapter.save(Transfer.create(date, Money.of(amount), "Traspaso", from, to));
    }

    @Test
    void save_roundTripsThroughTheMapper() {
        seedAccounts();

        Transfer saved = save(corriente, ahorro, "100", JAN);

        assertThat(saved.id()).isNotNull();
        assertThat(adapter.findById(saved.id())).hasValueSatisfying(t -> {
            assertThat(t.amount()).isEqualTo(Money.of("100"));
            assertThat(t.fromAccountId()).isEqualTo(corriente);
            assertThat(t.toAccountId()).isEqualTo(ahorro);
            assertThat(t.date()).isEqualTo(JAN);
        });
    }

    @Test
    void save_existingTransfer_updatesInPlace() {
        seedAccounts();
        Transfer saved = save(corriente, ahorro, "100", JAN);

        saved.reassign(MAR, Money.of("250"), "Editado", ahorro, corriente);
        Transfer reSaved = adapter.save(saved);

        assertThat(reSaved.id()).isEqualTo(saved.id());
        assertThat(adapter.findById(saved.id())).hasValueSatisfying(t -> {
            assertThat(t.amount()).isEqualTo(Money.of("250"));
            assertThat(t.fromAccountId()).isEqualTo(ahorro);
            assertThat(t.toAccountId()).isEqualTo(corriente);
        });
    }

    @Test
    void delete_removesTheRow() {
        seedAccounts();
        Transfer saved = save(corriente, ahorro, "100", JAN);

        adapter.deleteById(saved.id());

        assertThat(adapter.findById(saved.id())).isEmpty();
    }

    @Test
    void queryAdapter_searchByAccountMatchesEitherSideNewestFirst() {
        seedAccounts();
        AccountId tercera = accounts.save(Account.create("Tercera", "Banco", Money.zero())).id();
        save(corriente, ahorro, "100", JAN); // ahorro is destination
        save(ahorro, tercera, "50", FEB);     // ahorro is source
        save(corriente, tercera, "70", FEB);  // ahorro on neither side

        List<TransferView> views = queries.search(WIDE_FROM, WIDE_TO, ahorro.value());

        // Both transfers touching ahorro, newest first (the FEB one leads).
        assertThat(views).hasSize(2);
        assertThat(views.get(0).date()).isEqualTo(FEB);
        assertThat(views).extracting(v -> v.amount())
                .usingElementComparator(java.math.BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new java.math.BigDecimal("100"), new java.math.BigDecimal("50"));
    }

    @Test
    void queryAdapter_assemblesNestedAccountRefs() {
        seedAccounts();
        Transfer saved = save(corriente, ahorro, "100", JAN);

        assertThat(queries.findById(saved.id())).hasValueSatisfying(v -> {
            assertThat(v.amount()).isEqualByComparingTo("100");
            assertThat(v.fromAccount().id()).isEqualTo(corriente.value());
            assertThat(v.fromAccount().name()).isEqualTo("Corriente");
            assertThat(v.toAccount().id()).isEqualTo(ahorro.value());
            assertThat(v.toAccount().name()).isEqualTo("Ahorro");
        });
    }
}
