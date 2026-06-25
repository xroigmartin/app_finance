package com.xroig.finance.categories.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.accounts.domain.Account;
import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaMapper;
import com.xroig.finance.accounts.infrastructure.persistence.AccountPersistenceAdapter;
import com.xroig.finance.categories.application.CategoryView;
import com.xroig.finance.categories.domain.Category;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categories.domain.CategoryScope;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.shared.domain.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapter test (Level 2) against real PostgreSQL: the {@link CategoryPersistenceAdapter}
 * round-trip through {@link CategoryJpaMapper} (the pure aggregate survives, scope and
 * parent referenced by id), the children lookups, and the read-side
 * {@link CategoryQueryAdapter} assembling the nested {@link CategoryView}.
 */
@Import({CategoryPersistenceAdapter.class, CategoryQueryAdapter.class, CategoryJpaMapper.class,
        AccountPersistenceAdapter.class, AccountJpaMapper.class})
class CategoryPersistenceAdapterTest extends PostgresTestBase {

    @Autowired private CategoryPersistenceAdapter adapter;
    @Autowired private CategoryQueryAdapter queries;
    @Autowired private AccountPersistenceAdapter accounts;

    private AccountId anAccount() {
        return accounts.save(Account.create("Corriente", "Banco", Money.of("100"))).id();
    }

    @Test
    void save_topLevelBound_roundTripsThroughTheMapper() {
        AccountId acc = anAccount();

        Category saved = adapter.save(
                Category.createTopLevel("Comida", TransactionType.EXPENSE, "#def", CategoryScope.boundTo(acc)));

        assertThat(saved.id()).isNotNull();
        assertThat(adapter.findById(saved.id())).hasValueSatisfying(c -> {
            assertThat(c.name()).isEqualTo("Comida");
            assertThat(c.type()).isEqualTo(TransactionType.EXPENSE);
            assertThat(c.isTopLevel()).isTrue();
            assertThat(c.scope()).isEqualTo(CategoryScope.boundTo(acc));
        });
    }

    @Test
    void save_subcategory_linksParentAndChildrenLookupsWork() {
        AccountId acc = anAccount();
        Category parent = adapter.save(
                Category.createTopLevel("Hogar", TransactionType.EXPENSE, "#000", CategoryScope.boundTo(acc)));
        Category child = adapter.save(
                Category.createSubcategory("Luz", "#111", CategoryScope.boundTo(acc), parent));

        assertThat(child.parentId()).isEqualTo(parent.id());
        assertThat(adapter.existsByParentId(parent.id())).isTrue();
        assertThat(adapter.existsByParentId(child.id())).isFalse();
        assertThat(adapter.findChildren(parent.id())).extracting(c -> c.id().value())
                .containsExactly(child.id().value());
    }

    @Test
    void save_existingCategory_updatesInPlace() {
        AccountId acc = anAccount();
        Category saved = adapter.save(
                Category.createTopLevel("Viejo", TransactionType.EXPENSE, "#000", CategoryScope.global()));

        saved.makeTopLevel("Nuevo", TransactionType.INCOME, "#abc", CategoryScope.boundTo(acc));
        Category reSaved = adapter.save(saved);

        assertThat(reSaved.id()).isEqualTo(saved.id());
        assertThat(adapter.findById(saved.id())).hasValueSatisfying(c -> {
            assertThat(c.name()).isEqualTo("Nuevo");
            assertThat(c.type()).isEqualTo(TransactionType.INCOME);
            assertThat(c.scope()).isEqualTo(CategoryScope.boundTo(acc));
        });
    }

    @Test
    void delete_removesTheRow() {
        Category global = adapter.save(
                Category.createTopLevel("Suministros", TransactionType.EXPENSE, "#abc", CategoryScope.global()));

        adapter.deleteById(global.id());

        assertThat(adapter.findById(global.id())).isEmpty();
    }

    @Test
    void queryAdapter_assemblesNestedAccountAndParentViews() {
        AccountId acc = anAccount();
        Category parent = adapter.save(
                Category.createTopLevel("Hogar", TransactionType.EXPENSE, "#000", CategoryScope.boundTo(acc)));
        Category child = adapter.save(
                Category.createSubcategory("Luz", "#111", CategoryScope.boundTo(acc), parent));

        CategoryView view = queries.findById(child.id()).orElseThrow();

        assertThat(view.name()).isEqualTo("Luz");
        assertThat(view.account()).isNotNull();
        assertThat(view.account().id()).isEqualTo(acc.value());
        assertThat(view.account().name()).isEqualTo("Corriente");
        assertThat(view.parent()).isNotNull();
        assertThat(view.parent().id()).isEqualTo(parent.id().value());
        assertThat(view.parent().parent()).isNull();
    }

    @Test
    void queryAdapter_globalCategoryHasNoNestedAccount() {
        Category global = adapter.save(
                Category.createTopLevel("Suministros", TransactionType.INCOME, "#abc", CategoryScope.global()));

        CategoryView view = queries.findById(global.id()).orElseThrow();

        assertThat(view.account()).isNull();
        assertThat(view.parent()).isNull();
        assertThat(view.type()).isEqualTo(TransactionType.INCOME);
        assertThat(queries.findAll()).extracting(CategoryView::id).contains(global.id().value());
    }
}
