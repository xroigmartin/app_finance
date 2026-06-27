package com.xroig.finance.budgets.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.budgets.application.RecurringBudgetView;
import com.xroig.finance.budgets.domain.MonthsMask;
import com.xroig.finance.budgets.domain.RecurrenceAmount;
import com.xroig.finance.budgets.domain.RecurringBudget;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.model.Account;
import com.xroig.finance.model.Category;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.TransactionType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Read-model test (Level 2) for {@link RecurringBudgetQueryAdapter}: the {@link RecurringBudgetView}
 * exposes the months as a sorted 1..12 list, the active flag and the amount rows sorted by
 * effective month, each carrying its persistence id (the form edits them in place).
 */
@Import({RecurringBudgetQueryAdapter.class, RecurringBudgetPersistenceAdapter.class, RecurringBudgetJpaMapper.class})
class RecurringBudgetQueryAdapterTest extends PostgresTestBase {

    @Autowired private RecurringBudgetQueryAdapter queryAdapter;
    @Autowired private RecurringBudgetPersistenceAdapter persistenceAdapter;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private EntityManager em;

    @Test
    void find_isEmptyWhenTheCategoryHasNoRecurrence() {
        Category c = category("Sin recurrencia", account("Corriente"));
        assertThat(queryAdapter.find(new CategoryId(c.getId()))).isEmpty();
    }

    @Test
    void find_assemblesTheViewWithSortedMonthsAndAmountsCarryingIds() {
        Category comunidad = category("Comunidad", account("Corriente"));
        persistenceAdapter.save(RecurringBudget.create(new CategoryId(comunidad.getId()),
                MonthsMask.ofMonths(List.of(12, 1)), true,
                List.of(amount("120", "2024-06"), amount("100", "2024-01"))));
        em.flush();
        em.clear();

        RecurringBudgetView view = queryAdapter.find(new CategoryId(comunidad.getId())).orElseThrow();

        assertThat(view.categoryId()).isEqualTo(comunidad.getId());
        assertThat(view.active()).isTrue();
        assertThat(view.months()).containsExactly(1, 12);
        assertThat(view.amounts()).extracting(RecurringBudgetView.AmountView::validoDesde)
                .containsExactly("2024-01", "2024-06"); // sorted by effective month
        assertThat(view.amounts()).allSatisfy(a -> assertThat(a.id()).isNotNull());
        assertThat(view.amounts().get(0).amount()).isEqualByComparingTo("100");
    }

    // ---- helpers ----

    private static RecurrenceAmount amount(String value, String yearMonth) {
        return new RecurrenceAmount(Money.of(value), YearMonth.parse(yearMonth));
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
        c.setColor("#000000");
        c.setAccount(account);
        return categoryRepository.save(c);
    }
}
