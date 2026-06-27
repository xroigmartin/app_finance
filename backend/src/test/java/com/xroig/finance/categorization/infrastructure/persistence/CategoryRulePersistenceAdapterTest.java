package com.xroig.finance.categorization.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categorization.application.CategoryRuleView;
import com.xroig.finance.categorization.domain.CategoryRule;
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
 * Adapter test (Level 2) against real PostgreSQL: the {@link CategoryRulePersistenceAdapter}
 * round-trip through {@link CategoryRuleJpaMapper} (the pure aggregate survives, target
 * category by id), the {@code existsByCategory} guard, and the read-side {@link
 * CategoryRuleQueryAdapter} assembling the nested category in {@link CategoryRuleView}.
 */
@Import({CategoryRulePersistenceAdapter.class, CategoryRuleJpaMapper.class, CategoryRuleQueryAdapter.class})
class CategoryRulePersistenceAdapterTest extends PostgresTestBase {

    @Autowired private CategoryRulePersistenceAdapter adapter;
    @Autowired private CategoryRuleQueryAdapter queries;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CategoryRepository categoryRepository;

    @Test
    void save_roundTripsThroughTheMapper() {
        CategoryId cat = new CategoryId(category("Supermercado", null).getId());

        CategoryRule saved = adapter.save(CategoryRule.create("lidl|mercadona", cat));

        assertThat(saved.id()).isNotNull();
        assertThat(adapter.findById(saved.id())).hasValueSatisfying(r -> {
            assertThat(r.pattern()).isEqualTo("lidl|mercadona");
            assertThat(r.categoryId()).isEqualTo(cat);
        });
    }

    @Test
    void save_existingRule_updatesInPlace() {
        CategoryId first = new CategoryId(category("Supermercado", null).getId());
        CategoryId second = new CategoryId(category("Nómina", null).getId());
        CategoryRule saved = adapter.save(CategoryRule.create("viejo", first));

        saved.changeTo("nuevo", second);
        CategoryRule reSaved = adapter.save(saved);

        assertThat(reSaved.id()).isEqualTo(saved.id());
        assertThat(adapter.findById(saved.id())).hasValueSatisfying(r -> {
            assertThat(r.pattern()).isEqualTo("nuevo");
            assertThat(r.categoryId()).isEqualTo(second);
        });
    }

    @Test
    void delete_removesTheRow_andExistsByCategoryReflectsIt() {
        Category cat = category("Supermercado", null);
        CategoryId catId = new CategoryId(cat.getId());
        CategoryRule saved = adapter.save(CategoryRule.create("lidl", catId));

        assertThat(adapter.existsByCategory(catId)).isTrue();
        assertThat(adapter.existsByCategory(new CategoryId(cat.getId() + 999))).isFalse();

        adapter.deleteById(saved.id());

        assertThat(adapter.findById(saved.id())).isEmpty();
        assertThat(adapter.existsByCategory(catId)).isFalse();
    }

    @Test
    void queryAdapter_assemblesNestedAccountBoundCategory() {
        Account account = account("Corriente");
        Category cat = category("Supermercado", account);
        CategoryRule saved = adapter.save(CategoryRule.create("lidl", new CategoryId(cat.getId())));

        assertThat(queries.findById(saved.id())).hasValueSatisfying(v -> {
            assertThat(v.pattern()).isEqualTo("lidl");
            assertThat(v.category().id()).isEqualTo(cat.getId());
            assertThat(v.category().name()).isEqualTo("Supermercado");
            assertThat(v.category().type()).isEqualTo(TransactionType.EXPENSE);
            assertThat(v.category().account().id()).isEqualTo(account.getId());
            assertThat(v.category().account().name()).isEqualTo("Corriente");
        });
    }

    @Test
    void queryAdapter_findAll_listsTheRules() {
        CategoryId cat = new CategoryId(category("Supermercado", null).getId());
        adapter.save(CategoryRule.create("lidl", cat));

        assertThat(queries.findAll()).extracting(CategoryRuleView::pattern).contains("lidl");
    }

    private Account account(String name) {
        Account a = new Account();
        a.setName(name);
        a.setType("Banco");
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
