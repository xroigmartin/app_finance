package com.xroig.finance.repository;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.model.Account;
import com.xroig.finance.model.Category;
import com.xroig.finance.model.RecurringBudget;
import com.xroig.finance.model.RecurringBudgetAmount;
import com.xroig.finance.shared.domain.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Level-2 tests for the matrix-feeding queries of {@link RecurringBudgetRepository}
 * against real PostgreSQL (stage T4, repository query coverage):
 * {@code findActiveByAccountWithAmounts} and {@code findAllActiveWithAmounts}.
 *
 * <p>They exercise what only the DB shows: the {@code active = true} filter, the
 * account scoping via {@code category.account.id}, and that the {@code distinct}
 * on a {@code left join fetch} of the amount history collapses the cartesian rows
 * back to one recurrence root while still eagerly loading every amount.
 */
class RecurringBudgetRepositoryTest extends PostgresTestBase {

    @Autowired private RecurringBudgetRepository recurringRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AccountRepository accountRepository;

    @Test
    void findActiveByAccountWithAmounts_scopesByAccountSkipsInactiveAndFetchesAmounts() {
        Account corriente = account("Corriente");
        Account ahorro = account("Ahorro");

        // Active, owned by 'corriente', with two amounts → must come back once, fully loaded.
        recurrence(category("Comunidad", corriente), true,
                amount("100", "2024-01"), amount("120", "2024-06"));
        // Inactive on the same account → filtered out.
        recurrence(category("Gimnasio", corriente), false, amount("40", "2024-01"));
        // Active but on another account → out of scope.
        recurrence(category("Seguro", ahorro), true, amount("60", "2024-01"));

        List<RecurringBudget> result = recurringRepository.findActiveByAccountWithAmounts(corriente.getId());

        assertThat(result).hasSize(1);
        RecurringBudget r = result.get(0);
        assertThat(r.getCategory().getName()).isEqualTo("Comunidad");
        assertThat(r.getAmounts())
                .extracting(RecurringBudgetAmount::getValidoDesde)
                .containsExactlyInAnyOrder(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 1));
    }

    @Test
    void findAllActiveWithAmounts_returnsActiveAcrossAccountsWithoutDuplicates() {
        Account corriente = account("Corriente");
        Account ahorro = account("Ahorro");
        recurrence(category("Comunidad", corriente), true,
                amount("100", "2024-01"), amount("120", "2024-06"));
        recurrence(category("Seguro", ahorro), true, amount("60", "2024-01"));
        recurrence(category("Gimnasio", corriente), false, amount("40", "2024-01")); // inactive

        List<RecurringBudget> result = recurringRepository.findAllActiveWithAmounts();

        // Both active recurrences, each as a single root despite the amount join.
        assertThat(result).extracting(r -> r.getCategory().getName())
                .containsExactlyInAnyOrder("Comunidad", "Seguro");
    }

    // ---- helpers ----

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

    private void recurrence(Category category, boolean active, RecurringBudgetAmount... amounts) {
        RecurringBudget r = new RecurringBudget();
        r.setCategory(category);
        r.setMonths(0b1);
        r.setActive(active);
        for (RecurringBudgetAmount a : amounts) {
            a.setRecurringBudget(r);
            r.getAmounts().add(a);
        }
        recurringRepository.save(r);
    }

    private RecurringBudgetAmount amount(String value, String yearMonth) {
        RecurringBudgetAmount a = new RecurringBudgetAmount();
        a.setAmount(new BigDecimal(value));
        a.setValidoDesde(LocalDate.parse(yearMonth + "-01"));
        return a;
    }
}
