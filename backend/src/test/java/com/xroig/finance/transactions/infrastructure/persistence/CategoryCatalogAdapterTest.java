package com.xroig.finance.transactions.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.accounts.domain.Account;
import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaMapper;
import com.xroig.finance.accounts.infrastructure.persistence.AccountPersistenceAdapter;
import com.xroig.finance.categories.domain.Category;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categories.domain.CategoryScope;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaMapper;
import com.xroig.finance.categories.infrastructure.persistence.CategoryPersistenceAdapter;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.transactions.domain.CategoryCatalog.CategoryDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapter test (Level 2) for {@link CategoryCatalogAdapter}: it reports a category's
 * owning account (or global) so the transactions context can validate the
 * category↔account match, reading from the categories store.
 */
@Import({CategoryCatalogAdapter.class, CategoryPersistenceAdapter.class, CategoryJpaMapper.class,
        AccountPersistenceAdapter.class, AccountJpaMapper.class})
class CategoryCatalogAdapterTest extends PostgresTestBase {

    @Autowired private CategoryCatalogAdapter adapter;
    @Autowired private CategoryPersistenceAdapter categories;
    @Autowired private AccountPersistenceAdapter accounts;

    @Test
    void find_accountBoundCategory_reportsItsAccount() {
        AccountId acc = accounts.save(Account.create("Corriente", "Banco", Money.zero())).id();
        CategoryId id = categories.save(Category.createTopLevel("Comida", TransactionType.EXPENSE, "#000",
                CategoryScope.boundTo(acc))).id();

        assertThat(adapter.find(id)).hasValueSatisfying(d -> {
            assertThat(d.id()).isEqualTo(id);
            assertThat(d.isGlobal()).isFalse();
            assertThat(d.accountId()).isEqualTo(acc);
        });
    }

    @Test
    void find_globalCategory_reportsGlobal() {
        CategoryId id = categories.save(Category.createTopLevel("Suministros", TransactionType.EXPENSE, "#000",
                CategoryScope.global())).id();

        assertThat(adapter.find(id)).hasValueSatisfying(d -> {
            assertThat(d.isGlobal()).isTrue();
            assertThat(d.accountId()).isNull();
        });
    }

    @Test
    void find_unknownCategory_isEmpty() {
        assertThat(adapter.find(new CategoryId(-1L))).isEmpty();
    }
}
