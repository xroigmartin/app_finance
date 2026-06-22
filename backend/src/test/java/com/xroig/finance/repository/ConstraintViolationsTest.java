package com.xroig.finance.repository;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.controller.GlobalExceptionHandler;
import com.xroig.finance.model.Account;
import com.xroig.finance.model.Budget;
import com.xroig.finance.model.Category;
import com.xroig.finance.model.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Level-2 tests for the UNIQUE constraints that feed {@link GlobalExceptionHandler}
 * (stage T3), against real PostgreSQL. The E3e unit test already checks the
 * handler's message mapping, but it does so with <b>fabricated</b> cause messages;
 * it cannot prove that the markers it keys on (e.g. {@code ux_categories_name_scope})
 * are the constraints' <b>real</b> Postgres names. Here we provoke each violation
 * on the live schema and run the resulting {@link DataIntegrityViolationException}
 * through the actual handler, so a rename in a migration that broke the mapping
 * would fail this test.
 *
 * <p>The "no viola" cases pin the partial shape of those constraints — the
 * {@code coalesce(account_id, 0)} / {@code coalesce(parent_id, 0)} scoping of the
 * category-name index and the period dimension of the budget key — which is the
 * whole point of their column lists.
 */
class ConstraintViolationsTest extends PostgresTestBase {

    @Autowired private AccountRepository accountRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private BudgetRepository budgetRepository;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ---------- ux_categories_name_scope (name, coalesce(account), coalesce(parent)) ----------

    @Test
    void categoryName_duplicateInSameScope_maps409() {
        Account corriente = account("Corriente");
        category("Comida", corriente, null);

        ProblemDetail pd = violationHandledBy(() -> category("Comida", corriente, null));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getDetail()).isEqualTo("Ya existe una categoría con ese nombre en ese ámbito.");
    }

    @Test
    void categoryName_sameNameDifferentAccountOrParent_isAllowed() {
        Account a = account("Cuenta A");
        Account b = account("Cuenta B");
        Category padre = category("Hogar", a, null);
        Category otroPadre = category("Casa", a, null);
        category("Comida", a, null);

        // Same name on a different account: different coalesce(account_id) → allowed.
        // Same name under two different parents on the same account → allowed.
        assertThatCode(() -> {
            category("Comida", b, null);
            category("Luz", a, padre);
            category("Luz", a, otroPadre);
        }).doesNotThrowAnyException();
    }

    // ---------- uq_monthly_budgets_account_category_period (account, category, year, month) ----------

    @Test
    void budgetPeriod_duplicateForSameAccountCategoryPeriod_maps409() {
        Account corriente = account("Corriente");
        Category comida = category("Comida", corriente, null);
        budget(corriente, comida, 2024, 3, "100");

        ProblemDetail pd = violationHandledBy(() -> budget(corriente, comida, 2024, 3, "150"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getDetail()).isEqualTo("Ya existe un presupuesto para esa categoría, cuenta y periodo.");
    }

    @Test
    void budgetPeriod_differentMonthSameCategory_isAllowed() {
        Account corriente = account("Corriente");
        Category comida = category("Comida", corriente, null);
        budget(corriente, comida, 2024, 3, "100");

        assertThatCode(() -> budget(corriente, comida, 2024, 4, "100"))
                .doesNotThrowAnyException();
    }

    // ---- helpers ----

    /**
     * Runs {@code action}, asserts it raised a {@link DataIntegrityViolationException}
     * on flush, and returns what the real handler makes of it.
     */
    private ProblemDetail violationHandledBy(Runnable action) {
        DataIntegrityViolationException ex =
                catchThrowableOfType(DataIntegrityViolationException.class, action::run);
        assertThat(ex).as("expected a constraint violation").isNotNull();
        return handler.handleDataIntegrityViolation(ex);
    }

    private Account account(String name) {
        Account a = new Account();
        a.setName(name);
        a.setType("CORRIENTE");
        a.setInitialBalance(BigDecimal.ZERO);
        return accountRepository.saveAndFlush(a);
    }

    private Category category(String name, Account account, Category parent) {
        Category c = new Category();
        c.setName(name);
        c.setType(TransactionType.EXPENSE);
        c.setColor("#" + name.toLowerCase());
        c.setAccount(account);
        c.setParent(parent);
        return categoryRepository.saveAndFlush(c);
    }

    private Budget budget(Account account, Category category, int year, int month, String amount) {
        Budget b = new Budget();
        b.setAccount(account);
        b.setCategory(category);
        b.setYear(year);
        b.setMonth(month);
        b.setAmount(new BigDecimal(amount));
        return budgetRepository.saveAndFlush(b);
    }
}
