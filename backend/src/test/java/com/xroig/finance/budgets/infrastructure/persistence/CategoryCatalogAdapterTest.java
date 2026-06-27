package com.xroig.finance.budgets.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.budgets.domain.CategoryCatalog.CategoryBudgetInfo;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.model.Account;
import com.xroig.finance.model.Category;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.shared.domain.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapter test (Level 2) for the budgets {@link CategoryCatalogAdapter}: it reports a
 * category's owning account (or global) and whether it has subcategories, so the budgets
 * application can enforce the leaf and account-scope rules. Categories are seeded through
 * the legacy repository, which maps the same table.
 */
@Import(CategoryCatalogAdapter.class)
class CategoryCatalogAdapterTest extends PostgresTestBase {

    @Autowired private CategoryCatalogAdapter adapter;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CategoryRepository categoryRepository;

    @Test
    void find_leafAccountBoundCategory_reportsAccountAndNoChildren() {
        Account account = account("Corriente");
        Category comida = category("Comida", account, null);

        assertThat(adapter.find(new CategoryId(comida.getId()))).hasValueSatisfying((CategoryBudgetInfo info) -> {
            assertThat(info.id()).isEqualTo(new CategoryId(comida.getId()));
            assertThat(info.isGlobal()).isFalse();
            assertThat(info.accountId().value()).isEqualTo(account.getId());
            assertThat(info.hasChildren()).isFalse();
        });
    }

    @Test
    void find_parentWithSubcategory_reportsHasChildren() {
        Account account = account("Corriente");
        Category hogar = category("Hogar", account, null);
        category("Luz", account, hogar);

        assertThat(adapter.find(new CategoryId(hogar.getId())))
                .hasValueSatisfying(info -> assertThat(info.hasChildren()).isTrue());
    }

    @Test
    void find_globalCategory_reportsGlobal() {
        Category global = category("Suministros", null, null);

        assertThat(adapter.find(new CategoryId(global.getId()))).hasValueSatisfying(info -> {
            assertThat(info.isGlobal()).isTrue();
            assertThat(info.accountId()).isNull();
            assertThat(info.hasChildren()).isFalse();
        });
    }

    @Test
    void find_unknownCategory_isEmpty() {
        assertThat(adapter.find(new CategoryId(-1L))).isEmpty();
    }

    private Account account(String name) {
        Account a = new Account();
        a.setName(name);
        a.setType("Banco");
        a.setInitialBalance(BigDecimal.ZERO);
        return accountRepository.save(a);
    }

    private Category category(String name, Account account, Category parent) {
        Category c = new Category();
        c.setName(name);
        c.setType(TransactionType.EXPENSE);
        c.setColor("#000000");
        c.setAccount(account);
        c.setParent(parent);
        return categoryRepository.save(c);
    }
}
