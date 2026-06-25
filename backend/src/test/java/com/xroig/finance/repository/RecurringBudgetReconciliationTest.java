package com.xroig.finance.repository;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.dto.RecurringBudgetDtos.AmountRequest;
import com.xroig.finance.dto.RecurringBudgetDtos.RecurringBudgetRequest;
import com.xroig.finance.model.Account;
import com.xroig.finance.model.Category;
import com.xroig.finance.model.RecurringBudget;
import com.xroig.finance.model.RecurringBudgetAmount;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.service.RecurringBudgetService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Level-2 tests for the recurrence reconciliation against real PostgreSQL
 * (stage T2). The Mockito tests at E2a already pin the in-memory bookkeeping of
 * {@link RecurringBudgetService#upsert}; what they cannot prove is what only a
 * real Hibernate flush against the real schema shows:
 *
 * <ul>
 *   <li>editing an amount while keeping its effective month does <b>not</b>
 *       violate {@code uq_amount_vigencia}. Hibernate flushes inserts before
 *       deletes, so the naive "clear and reinsert" reconciliation would insert a
 *       second row with the same {@code (recurring_budget_id, valido_desde)}
 *       before deleting the old one — the exact bug the in-place update fixes;</li>
 *   <li>the {@code uq_amount_vigencia} UNIQUE and {@code chk_recurring_months}
 *       CHECK constraints are actually enforced by the DB (they feed the
 *       {@code GlobalExceptionHandler} mapping covered later);</li>
 *   <li>orphaned amounts are physically deleted, and deleting a recurrence
 *       cascades to its amount history.</li>
 * </ul>
 *
 * <p>The service is pulled into the {@code @DataJpaTest} slice with {@code @Import}.
 * Each scenario forces an {@link EntityManager#flush()} so the SQL — and any
 * constraint violation — actually hits the database, and a {@code clear()} between
 * upserts so the second call reloads the recurrence from the DB, reproducing the
 * "edit in a later request" path rather than mutating an already-managed graph.
 */
@Import(RecurringBudgetService.class)
class RecurringBudgetReconciliationTest extends PostgresTestBase {

    @Autowired private RecurringBudgetService service;
    @Autowired private RecurringBudgetRepository recurringRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private EntityManager em;

    private Category comunidad;

    @BeforeEach
    void setUp() {
        Account corriente = account("Corriente");
        comunidad = category("Comunidad", corriente);
    }

    // ---------- the headline: edit keeping the effective month ----------

    @Test
    void upsert_editingAmountKeepingMonth_doesNotViolateUniqueVigencia() {
        service.upsert(comunidad.getId(), request(List.of(1), true, amount("100", "2024-01")));
        flushAndClear();

        // Same validoDesde, new amount: the buggy clear+reinsert would insert a
        // second (recurring_budget_id, 2024-01) row before deleting the old one.
        service.upsert(comunidad.getId(), request(List.of(1), true, amount("200", "2024-01")));
        em.flush();
        em.clear();

        List<RecurringBudgetAmount> amounts = reloadAmounts();
        assertThat(amounts).hasSize(1);
        assertThat(amounts.get(0).getAmount()).isEqualByComparingTo("200");
        assertThat(amounts.get(0).getValidoDesde()).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    // ---------- full reconcile across a real reload: keep, update, drop, add ----------

    @Test
    void upsert_reconcilesAcrossReload_keepsUpdatesDropsAndAdds() {
        service.upsert(comunidad.getId(), request(List.of(1), true,
                amount("100", "2024-01"), amount("200", "2024-06")));
        flushAndClear();

        // 2024-01 updated in place, 2024-06 dropped, 2024-09 added.
        service.upsert(comunidad.getId(), request(List.of(3, 12), false,
                amount("150", "2024-01"), amount("300", "2024-09")));
        flushAndClear();

        RecurringBudget reloaded = recurringRepository
                .findByCategoryIdWithAmounts(comunidad.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
        assertThat(reloaded.getMonths()).isEqualTo(0b1000_0000_0100); // months 3 and 12
        assertThat(reloaded.getAmounts())
                .extracting(RecurringBudgetAmount::getValidoDesde)
                .containsExactlyInAnyOrder(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 9, 1));
        assertThat(amountAt(reloaded, "2024-01")).isEqualByComparingTo("150");
        assertThat(amountAt(reloaded, "2024-09")).isEqualByComparingTo("300");
        // The dropped 2024-06 row is physically gone, not just detached.
        assertThat(countAmountRows()).isEqualTo(2);
    }

    // ---------- the constraints are real ----------

    @Test
    void uniqueVigencia_rejectsTwoAmountsWithSameValidoDesde() {
        RecurringBudget recurrence = new RecurringBudget();
        recurrence.setCategory(comunidad);
        recurrence.setMonths(0b1);
        recurrence.getAmounts().add(amountRow(recurrence, "100", "2024-01"));
        recurrence.getAmounts().add(amountRow(recurrence, "120", "2024-01"));

        assertThatThrownBy(() -> recurringRepository.saveAndFlush(recurrence))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void checkRecurringMonths_rejectsEmptyBitmask() {
        RecurringBudget recurrence = new RecurringBudget();
        recurrence.setCategory(comunidad);
        recurrence.setMonths(0); // violates chk_recurring_months (months > 0)

        assertThatThrownBy(() -> recurringRepository.saveAndFlush(recurrence))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oneToOneCategory_rejectsSecondRecurrenceOnSameCategory() {
        service.upsert(comunidad.getId(), request(List.of(1), true, amount("100", "2024-01")));
        flushAndClear();

        RecurringBudget duplicate = new RecurringBudget();
        duplicate.setCategory(categoryRepository.findById(comunidad.getId()).orElseThrow());
        duplicate.setMonths(0b1);
        duplicate.getAmounts().add(amountRow(duplicate, "50", "2024-02"));

        assertThatThrownBy(() -> recurringRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- cascade delete of the amount history ----------

    @Test
    void delete_cascadesToAmountHistory() {
        service.upsert(comunidad.getId(), request(List.of(1), true,
                amount("100", "2024-01"), amount("200", "2024-06")));
        flushAndClear();
        assertThat(countAmountRows()).isEqualTo(2);

        service.delete(comunidad.getId());
        em.flush();

        assertThat(recurringRepository.findByCategoryIdWithAmounts(comunidad.getId())).isEmpty();
        assertThat(countAmountRows()).isZero();
    }

    // ---- persistence + request helpers ----

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
        c.setColor("#" + name.toLowerCase());
        c.setAccount(account);
        return categoryRepository.save(c);
    }

    private static RecurringBudgetRequest request(List<Integer> months, boolean active, AmountRequest... amounts) {
        return new RecurringBudgetRequest(months, active, List.of(amounts));
    }

    private static AmountRequest amount(String value, String yearMonth) {
        return new AmountRequest(new BigDecimal(value), yearMonth);
    }

    private static RecurringBudgetAmount amountRow(RecurringBudget recurrence, String value, String yearMonth) {
        RecurringBudgetAmount a = new RecurringBudgetAmount();
        a.setRecurringBudget(recurrence);
        a.setAmount(new BigDecimal(value));
        a.setValidoDesde(LocalDate.parse(yearMonth + "-01"));
        return a;
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private List<RecurringBudgetAmount> reloadAmounts() {
        return recurringRepository.findByCategoryIdWithAmounts(comunidad.getId())
                .orElseThrow().getAmounts();
    }

    private long countAmountRows() {
        return ((Number) em.createNativeQuery("select count(*) from recurring_budget_amounts")
                .getSingleResult()).longValue();
    }

    private static BigDecimal amountAt(RecurringBudget recurrence, String yearMonth) {
        return recurrence.amountAt(LocalDate.parse(yearMonth + "-01"));
    }
}
