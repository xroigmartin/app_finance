package com.xroig.finance.budgets.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.budgets.application.BudgetView;
import com.xroig.finance.budgets.domain.Budget;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.model.Account;
import com.xroig.finance.model.Category;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapter test (Level 2) against real PostgreSQL: the {@link BudgetPersistenceAdapter}
 * round-trip through {@link BudgetJpaMapper} (pure aggregate, account/category by id),
 * the {@code existsAt}/{@code findByYearMonth} command queries and the read-side
 * {@link BudgetQueryAdapter} assembling the nested {@link BudgetView}. Accounts and
 * categories are seeded through the legacy repositories, which map the same tables.
 */
@Import({BudgetPersistenceAdapter.class, BudgetJpaMapper.class, BudgetQueryAdapter.class,
        RecurringBudgetPersistenceAdapter.class, RecurringBudgetJpaMapper.class})
class BudgetPersistenceAdapterTest extends PostgresTestBase {

    @Autowired private BudgetPersistenceAdapter adapter;
    @Autowired private BudgetQueryAdapter queries;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CategoryRepository categoryRepository;

    private AccountId account;
    private CategoryId comida;
    private CategoryId ocio;

    private void seed() {
        Account corriente = new Account();
        corriente.setName("Corriente");
        corriente.setType("Banco");
        corriente.setInitialBalance(new BigDecimal("100"));
        account = new AccountId(accountRepository.save(corriente).getId());

        comida = new CategoryId(saveCategory("Comida", corriente).getId());
        ocio = new CategoryId(saveCategory("Ocio", corriente).getId());
    }

    private Category saveCategory(String name, Account owner) {
        Category c = new Category();
        c.setName(name);
        c.setType(TransactionType.EXPENSE);
        c.setColor("#abcdef");
        c.setAccount(owner);
        return categoryRepository.save(c);
    }

    @Test
    void save_roundTripsThroughTheMapper() {
        seed();

        Budget saved = adapter.save(Budget.create(account, comida, 2024, 3, Money.of("100")));

        assertThat(saved.id()).isNotNull();
        assertThat(adapter.findById(saved.id())).hasValueSatisfying(b -> {
            assertThat(b.accountId()).isEqualTo(account);
            assertThat(b.categoryId()).isEqualTo(comida);
            assertThat(b.year()).isEqualTo(2024);
            assertThat(b.month()).isEqualTo(3);
            assertThat(b.amount()).isEqualTo(Money.of("100"));
        });
    }

    @Test
    void save_existingBudget_updatesInPlace() {
        seed();
        Budget saved = adapter.save(Budget.create(account, comida, 2024, 3, Money.of("100")));

        saved.reassign(account, ocio, 2024, 5, Money.of("250"));
        Budget reSaved = adapter.save(saved);

        assertThat(reSaved.id()).isEqualTo(saved.id());
        assertThat(adapter.findById(saved.id())).hasValueSatisfying(b -> {
            assertThat(b.categoryId()).isEqualTo(ocio);
            assertThat(b.month()).isEqualTo(5);
            assertThat(b.amount()).isEqualTo(Money.of("250"));
        });
    }

    @Test
    void existsAt_tracksTheSlot() {
        seed();
        adapter.save(Budget.create(account, comida, 2024, 3, Money.of("100")));

        assertThat(adapter.existsAt(account, comida, 2024, 3)).isTrue();
        assertThat(adapter.existsAt(account, comida, 2024, 4)).isFalse();
        assertThat(adapter.existsAt(account, ocio, 2024, 3)).isFalse();
    }

    @Test
    void findByYearMonth_returnsTheMonthAcrossCategories() {
        seed();
        adapter.save(Budget.create(account, comida, 2024, 1, Money.of("100")));
        adapter.save(Budget.create(account, ocio, 2024, 1, Money.of("60")));
        adapter.save(Budget.create(account, comida, 2024, 2, Money.of("110")));

        List<Budget> january = adapter.findByYearMonth(2024, 1);

        assertThat(january).hasSize(2);
        assertThat(january).extracting(Budget::categoryId).containsExactlyInAnyOrder(comida, ocio);
    }

    @Test
    void delete_removesTheRow() {
        seed();
        Budget saved = adapter.save(Budget.create(account, comida, 2024, 3, Money.of("100")));

        adapter.deleteById(saved.id());

        assertThat(adapter.findById(saved.id())).isEmpty();
    }

    @Test
    void queryAdapter_assemblesNestedRefs() {
        seed();
        Budget saved = adapter.save(Budget.create(account, comida, 2024, 3, Money.of("100")));

        assertThat(queries.findById(saved.id())).hasValueSatisfying(v -> {
            assertThat(v.amount()).isEqualByComparingTo("100");
            assertThat(v.account().id()).isEqualTo(account.value());
            assertThat(v.account().name()).isEqualTo("Corriente");
            assertThat(v.category().id()).isEqualTo(comida.value());
            assertThat(v.category().name()).isEqualTo("Comida");
            assertThat(v.category().type()).isEqualTo(TransactionType.EXPENSE);
        });

        assertThat(queries.find(2024, 3, account.value())).singleElement()
                .satisfies(v -> assertThat(v.category().id()).isEqualTo(comida.value()));
        assertThat(queries.find(2024, 4, account.value())).isEmpty();

        // Aggregate scope (no account): the same month across all accounts.
        assertThat(queries.find(2024, 3, null)).singleElement()
                .satisfies(v -> assertThat(v.category().id()).isEqualTo(comida.value()));
    }
}
