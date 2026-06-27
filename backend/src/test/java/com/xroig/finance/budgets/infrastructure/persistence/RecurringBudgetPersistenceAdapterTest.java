package com.xroig.finance.budgets.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.budgets.domain.MonthsMask;
import com.xroig.finance.budgets.domain.RecurrenceAmount;
import com.xroig.finance.budgets.domain.RecurringBudget;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaRepository;
import com.xroig.finance.model.Account;
import com.xroig.finance.model.Category;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.TransactionType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Level-2 tests for {@link RecurringBudgetPersistenceAdapter} against real PostgreSQL
 * (replaces the legacy {@code RecurringBudgetReconciliationTest}/{@code RecurringBudgetRepositoryTest}):
 * round-trip of the aggregate, the in-place amount reconciliation (which only a real Hibernate
 * flush proves does not violate {@code uq_amount_vigencia}), the active/account scoping of the
 * matrix query, the cascade delete, and that the DB constraints are real.
 *
 * <p>Each scenario forces an {@link EntityManager#flush()}/{@code clear()} between saves so the
 * second {@code save} reloads from the DB — reproducing the "edit in a later request" path
 * rather than mutating an already-managed graph.
 */
@Import({RecurringBudgetPersistenceAdapter.class, RecurringBudgetJpaMapper.class})
class RecurringBudgetPersistenceAdapterTest extends PostgresTestBase {

    @Autowired private RecurringBudgetPersistenceAdapter adapter;
    @Autowired private RecurringBudgetJpaRepository jpa;
    @Autowired private CategoryJpaRepository categoryJpa;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private EntityManager em;

    private Category comunidad;

    @BeforeEach
    void setUp() {
        comunidad = category("Comunidad", account("Corriente"));
    }

    // ---------- round-trip ----------

    @Test
    void save_thenFindByCategory_roundTripsTheAggregate() {
        adapter.save(recurrence(comunidad, List.of(1, 6), true,
                amount("100", "2024-01"), amount("120", "2024-06")));
        flushAndClear();

        RecurringBudget loaded = adapter.findByCategory(catId()).orElseThrow();
        assertThat(loaded.id()).isNotNull();
        assertThat(loaded.categoryId()).isEqualTo(catId());
        assertThat(loaded.isActive()).isTrue();
        assertThat(loaded.months().toMonths()).containsExactly(1, 6);
        assertThat(loaded.amounts()).extracting(a -> a.from().toString())
                .containsExactlyInAnyOrder("2024-01", "2024-06");
    }

    // ---------- the headline: edit keeping the effective month ----------

    @Test
    void save_editingAmountKeepingMonth_doesNotViolateUniqueVigencia() {
        adapter.save(recurrence(comunidad, List.of(1), true, amount("100", "2024-01")));
        flushAndClear();

        // Same effective month, new amount: a naive clear+reinsert would insert a second
        // (recurring_budget_id, 2024-01) row before deleting the old one.
        adapter.save(recurrence(comunidad, List.of(1), true, amount("200", "2024-01")));
        flushAndClear();

        assertThat(countAmountRows()).isEqualTo(1);
        RecurringBudget loaded = adapter.findByCategory(catId()).orElseThrow();
        assertThat(loaded.amountAt(YearMonth.parse("2024-01"))).get().isEqualTo(Money.of("200"));
    }

    @Test
    void save_reconcilesAcrossReload_keepsUpdatesDropsAndAdds() {
        adapter.save(recurrence(comunidad, List.of(1), true,
                amount("100", "2024-01"), amount("200", "2024-06")));
        flushAndClear();

        // 2024-01 updated in place, 2024-06 dropped, 2024-09 added; mask and active replaced.
        adapter.save(recurrence(comunidad, List.of(3, 12), false,
                amount("150", "2024-01"), amount("300", "2024-09")));
        flushAndClear();

        RecurringBudget loaded = adapter.findByCategory(catId()).orElseThrow();
        assertThat(loaded.isActive()).isFalse();
        assertThat(loaded.months().toMonths()).containsExactly(3, 12);
        assertThat(loaded.amounts()).extracting(a -> a.from().toString())
                .containsExactlyInAnyOrder("2024-01", "2024-09");
        assertThat(loaded.amountAt(YearMonth.parse("2024-01"))).get().isEqualTo(Money.of("150"));
        assertThat(loaded.amountAt(YearMonth.parse("2024-09"))).get().isEqualTo(Money.of("300"));
        assertThat(countAmountRows()).isEqualTo(2); // the dropped 2024-06 row is physically gone
    }

    // ---------- matrix query: active + account scoping ----------

    @Test
    void findActiveByAccount_scopesByAccountAndSkipsInactive() {
        Account corriente = account("Cuenta A");
        Account ahorro = account("Cuenta B");
        Category activa = category("Comunidad A", corriente);
        Category inactiva = category("Gimnasio", corriente);
        Category otra = category("Seguro", ahorro);
        adapter.save(recurrence(activa, List.of(1), true, amount("100", "2024-01")));
        adapter.save(recurrence(inactiva, List.of(1), false, amount("40", "2024-01")));
        adapter.save(recurrence(otra, List.of(1), true, amount("60", "2024-01")));
        flushAndClear();

        assertThat(adapter.findActiveByAccount(corriente.getId()))
                .extracting(r -> r.categoryId().value()).containsExactly(activa.getId());
        assertThat(adapter.findActiveByAccount(null))
                .extracting(r -> r.categoryId().value())
                .containsExactlyInAnyOrder(activa.getId(), otra.getId());
    }

    // ---------- cascade delete ----------

    @Test
    void deleteByCategory_removesTheRecurrenceAndItsAmounts() {
        adapter.save(recurrence(comunidad, List.of(1), true,
                amount("100", "2024-01"), amount("200", "2024-06")));
        flushAndClear();
        assertThat(countAmountRows()).isEqualTo(2);

        adapter.deleteByCategory(catId());
        em.flush();

        assertThat(adapter.findByCategory(catId())).isEmpty();
        assertThat(countAmountRows()).isZero();
    }

    @Test
    void deleteByCategory_isNoOpWhenAbsent() {
        adapter.deleteByCategory(catId()); // nothing persisted yet
        em.flush();
        assertThat(jpa.existsByCategoryId(comunidad.getId())).isFalse();
    }

    // ---------- the constraints are real ----------

    @Test
    void uniqueVigencia_rejectsTwoAmountsWithSameValidoDesde() {
        RecurringBudgetJpaEntity entity = rawEntity(0b1);
        entity.getAmounts().add(rawAmount(entity, "100", "2024-01"));
        entity.getAmounts().add(rawAmount(entity, "120", "2024-01"));

        assertThatThrownBy(() -> jpa.saveAndFlush(entity)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void checkRecurringMonths_rejectsEmptyBitmask() {
        assertThatThrownBy(() -> jpa.saveAndFlush(rawEntity(0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oneToOneCategory_rejectsSecondRecurrenceOnSameCategory() {
        adapter.save(recurrence(comunidad, List.of(1), true, amount("100", "2024-01")));
        flushAndClear();

        RecurringBudgetJpaEntity duplicate = rawEntity(0b1);
        duplicate.getAmounts().add(rawAmount(duplicate, "50", "2024-02"));

        assertThatThrownBy(() -> jpa.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- helpers ----

    private CategoryId catId() {
        return new CategoryId(comunidad.getId());
    }

    private static RecurringBudget recurrence(Category category, List<Integer> months, boolean active,
                                              RecurrenceAmount... amounts) {
        return RecurringBudget.create(new CategoryId(category.getId()),
                MonthsMask.ofMonths(months), active, List.of(amounts));
    }

    private static RecurrenceAmount amount(String value, String yearMonth) {
        return new RecurrenceAmount(Money.of(value), YearMonth.parse(yearMonth));
    }

    private RecurringBudgetJpaEntity rawEntity(int monthsBitmask) {
        RecurringBudgetJpaEntity e = new RecurringBudgetJpaEntity();
        e.setCategory(categoryJpa.getReferenceById(comunidad.getId()));
        e.setMonths(monthsBitmask);
        e.setActive(true);
        return e;
    }

    private static RecurrenceAmountJpaEntity rawAmount(RecurringBudgetJpaEntity parent, String value, String yearMonth) {
        RecurrenceAmountJpaEntity a = new RecurrenceAmountJpaEntity();
        a.setRecurringBudget(parent);
        a.setAmount(new BigDecimal(value));
        a.setValidoDesde(LocalDate.parse(yearMonth + "-01"));
        return a;
    }

    private Account account(String name) {
        Account a = new Account();
        a.setName(name);
        a.setType("CORRIENTE");
        a.setInitialBalance(BigDecimal.ZERO);
        return accountRepository.save(a);
    }

    private Category category(String name, Account account) {
        Category c = new Category();
        c.setName(name);
        c.setType(TransactionType.EXPENSE);
        c.setColor("#" + name.toLowerCase().replace(" ", ""));
        c.setAccount(account);
        return categoryRepository.save(c);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private long countAmountRows() {
        return ((Number) em.createNativeQuery("select count(*) from recurring_budget_amounts")
                .getSingleResult()).longValue();
    }
}
