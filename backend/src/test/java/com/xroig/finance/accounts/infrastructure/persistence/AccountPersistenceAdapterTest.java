package com.xroig.finance.accounts.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.accounts.domain.Account;
import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.model.Category;
import com.xroig.finance.model.Transaction;
import com.xroig.finance.model.TransactionType;
import com.xroig.finance.model.Transfer;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.TransactionRepository;
import com.xroig.finance.repository.TransferRepository;
import com.xroig.finance.shared.domain.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapter test (Level 2) against real PostgreSQL: exercises the
 * {@link AccountPersistenceAdapter} + {@link AccountJpaMapper} round-trip (so the
 * pure domain {@link Account} survives a save/find/delete) and the
 * {@link AccountUsageAdapter} deletion guard against actual movement/transfer rows.
 */
@Import({AccountPersistenceAdapter.class, AccountJpaMapper.class, AccountUsageAdapter.class})
class AccountPersistenceAdapterTest extends PostgresTestBase {

    @Autowired private AccountPersistenceAdapter adapter;
    @Autowired private AccountUsageAdapter usage;
    @Autowired private AccountJpaRepository jpa;
    @Autowired private AccountRepository legacyAccounts;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransferRepository transferRepository;

    @Test
    void save_assignsIdentityAndMapsBackToDomain() {
        Account saved = adapter.save(Account.create("Corriente", "Banco", Money.of("100")));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.name()).isEqualTo("Corriente");
        assertThat(saved.type()).isEqualTo("Banco");
        assertThat(saved.initialBalance()).isEqualTo(Money.of("100"));
    }

    @Test
    void findById_returnsTheStoredAggregate() {
        AccountId id = adapter.save(Account.create("Ahorro", "Banco", Money.of("50"))).id();

        assertThat(adapter.findById(id)).hasValueSatisfying(a -> {
            assertThat(a.name()).isEqualTo("Ahorro");
            assertThat(a.initialBalance()).isEqualTo(Money.of("50"));
        });
        assertThat(adapter.findById(new AccountId(-1L))).isEmpty();
    }

    @Test
    void update_persistsEditedFields() {
        Account saved = adapter.save(Account.create("Viejo", "Banco", Money.zero()));
        saved.update("Nuevo", "AHORRO", Money.of("250"));

        Account reloaded = adapter.save(saved);

        assertThat(reloaded.id()).isEqualTo(saved.id());
        assertThat(adapter.findById(reloaded.id())).hasValueSatisfying(a -> {
            assertThat(a.name()).isEqualTo("Nuevo");
            assertThat(a.type()).isEqualTo("AHORRO");
            assertThat(a.initialBalance()).isEqualTo(Money.of("250"));
        });
    }

    @Test
    void findAll_andDeleteById() {
        AccountId id = adapter.save(Account.create("Borrable", "Banco", Money.zero())).id();
        assertThat(adapter.findAll()).extracting(a -> a.id().value()).contains(id.value());

        adapter.deleteById(id);

        assertThat(jpa.existsById(id.value())).isFalse();
    }

    @Test
    void hasMovements_isTrueOnlyForAnAccountWithTransactions() {
        AccountId withMovement = adapter.save(Account.create("Con movimiento", "Banco", Money.zero())).id();
        AccountId without = adapter.save(Account.create("Sin movimiento", "Banco", Money.zero())).id();
        persistExpense(withMovement, "20");

        assertThat(usage.hasMovements(withMovement)).isTrue();
        assertThat(usage.hasMovements(without)).isFalse();
    }

    @Test
    void hasTransfers_isTrueForBothEndsOfATransfer() {
        AccountId from = adapter.save(Account.create("Origen", "Banco", Money.zero())).id();
        AccountId to = adapter.save(Account.create("Destino", "Banco", Money.zero())).id();
        AccountId unrelated = adapter.save(Account.create("Ajena", "Banco", Money.zero())).id();
        persistTransfer(from, to, "15");

        assertThat(usage.hasTransfers(from)).isTrue();
        assertThat(usage.hasTransfers(to)).isTrue();
        assertThat(usage.hasTransfers(unrelated)).isFalse();
    }

    // ---- helpers: build legacy rows the usage guard queries ----

    private void persistExpense(AccountId accountId, String amount) {
        com.xroig.finance.model.Account account = legacyAccount(accountId);
        Category category = new Category();
        category.setName("Gastos-" + accountId.value());
        category.setType(TransactionType.EXPENSE);
        category.setColor("#000000");
        category.setAccount(account);
        category = categoryRepository.save(category);

        Transaction tx = new Transaction();
        tx.setType(TransactionType.EXPENSE);
        tx.setAmount(new BigDecimal(amount));
        tx.setAccount(account);
        tx.setCategory(category);
        tx.setDate(LocalDate.of(2024, 1, 15));
        transactionRepository.save(tx);
    }

    private void persistTransfer(AccountId from, AccountId to, String amount) {
        Transfer transfer = new Transfer();
        transfer.setAmount(new BigDecimal(amount));
        transfer.setFromAccount(legacyAccount(from));
        transfer.setToAccount(legacyAccount(to));
        transfer.setDate(LocalDate.of(2024, 1, 15));
        transferRepository.save(transfer);
    }

    /** The managed legacy entity for the same row the adapter persisted (FK reference). */
    private com.xroig.finance.model.Account legacyAccount(AccountId id) {
        return legacyAccounts.findById(id.value()).orElseThrow();
    }
}
