package com.xroig.finance.categories.domain;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure domain tests for the {@link Category} aggregate and {@link CategoryScope}:
 * the hierarchy/type invariants the legacy controller enforced by hand — single
 * level of nesting, a subcategory inheriting its parent's type, default colour and
 * mandatory fields.
 */
class CategoryTest {

    private static final AccountId ACC = new AccountId(1L);

    @Test
    void createTopLevel_global_defaultsColourWhenMissing() {
        Category c = Category.createTopLevel("Comida", TransactionType.EXPENSE, null, CategoryScope.global());

        assertThat(c.id()).isNull();
        assertThat(c.isTopLevel()).isTrue();
        assertThat(c.isGlobalScope()).isTrue();
        assertThat(c.color()).isEqualTo("#6366f1");
    }

    @Test
    void createTopLevel_accountBound_keepsScopeAndColour() {
        Category c = Category.createTopLevel("Comida", TransactionType.EXPENSE, "#64748b",
                CategoryScope.boundTo(ACC));

        assertThat(c.scope()).isEqualTo(CategoryScope.boundTo(ACC));
        assertThat(c.color()).isEqualTo("#64748b");
    }

    @Test
    void createTopLevel_blankColourFallsBackToDefault() {
        Category c = Category.createTopLevel("Comida", TransactionType.EXPENSE, "   ", CategoryScope.global());

        assertThat(c.color()).isEqualTo("#6366f1");
    }

    @Test
    void createTopLevel_rejectsBlankName() {
        assertThatThrownBy(() -> Category.createTopLevel(" ", TransactionType.EXPENSE, null, CategoryScope.global()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("nombre");
    }

    @Test
    void createTopLevel_rejectsNullName() {
        assertThatThrownBy(() -> Category.createTopLevel(null, TransactionType.EXPENSE, null, CategoryScope.global()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("nombre");
    }

    @Test
    void createTopLevel_rejectsNullType() {
        assertThatThrownBy(() -> Category.createTopLevel("Comida", null, null, CategoryScope.global()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("tipo");
    }

    @Test
    void createSubcategory_inheritsParentTypeAndLinksParent() {
        Category parent = Category.rehydrate(new CategoryId(20L), "Hogar", TransactionType.EXPENSE,
                "#000", CategoryScope.boundTo(ACC), null);

        // The requested type (INCOME) is irrelevant: a subcategory has no own type.
        Category child = Category.createSubcategory("Luz", "#fff", CategoryScope.boundTo(ACC), parent);

        assertThat(child.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(child.isTopLevel()).isFalse();
        assertThat(child.parentId()).isEqualTo(new CategoryId(20L));
    }

    @Test
    void createSubcategory_rejectsNonTopLevelParent() {
        Category subParent = Category.rehydrate(new CategoryId(20L), "Hogar", TransactionType.EXPENSE,
                "#000", CategoryScope.global(), new CategoryId(5L)); // already a child

        assertThatThrownBy(() -> Category.createSubcategory("Luz", "#fff", CategoryScope.global(), subParent))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("un nivel");
    }

    @Test
    void makeSubcategoryOf_convertsAndInheritsType() {
        Category existing = Category.rehydrate(new CategoryId(7L), "X", TransactionType.INCOME,
                "#000", CategoryScope.global(), null);
        Category parent = Category.rehydrate(new CategoryId(20L), "Hogar", TransactionType.EXPENSE,
                "#000", CategoryScope.boundTo(ACC), null);

        existing.makeSubcategoryOf(parent, CategoryScope.boundTo(ACC), "X", "#000");

        assertThat(existing.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(existing.parentId()).isEqualTo(new CategoryId(20L));
        assertThat(existing.scope()).isEqualTo(CategoryScope.boundTo(ACC));
    }

    @Test
    void makeTopLevel_clearsParentAndSetsOwnType() {
        Category existing = Category.rehydrate(new CategoryId(7L), "X", TransactionType.EXPENSE,
                "#000", CategoryScope.boundTo(ACC), new CategoryId(20L));

        existing.makeTopLevel("Nuevo", TransactionType.INCOME, "#abc", CategoryScope.global());

        assertThat(existing.isTopLevel()).isTrue();
        assertThat(existing.type()).isEqualTo(TransactionType.INCOME);
        assertThat(existing.name()).isEqualTo("Nuevo");
        assertThat(existing.isGlobalScope()).isTrue();
    }

    @Test
    void rehydrate_requiresIdentity() {
        assertThatThrownBy(() -> Category.rehydrate(null, "X", TransactionType.EXPENSE,
                "#000", CategoryScope.global(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void categoryId_rejectsNull() {
        assertThatThrownBy(() -> new CategoryId(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scope_globalAndBoundEqualityAndAccessors() {
        assertThat(CategoryScope.global().isGlobal()).isTrue();
        assertThat(CategoryScope.global().accountId()).isEmpty();
        assertThat(CategoryScope.boundTo(ACC).isBound()).isTrue();
        assertThat(CategoryScope.boundTo(ACC).accountId()).contains(ACC);
        assertThat(CategoryScope.boundTo(ACC)).isEqualTo(CategoryScope.boundTo(new AccountId(1L)));
        assertThat(CategoryScope.boundTo(ACC)).isNotEqualTo(CategoryScope.global());
    }

    @Test
    void scope_equalsHashCodeAndToString() {
        CategoryScope bound = CategoryScope.boundTo(ACC);
        assertThat(bound).isEqualTo(bound);                       // identity
        assertThat(bound).isNotEqualTo("otra cosa");              // different type
        assertThat(bound).isNotEqualTo(CategoryScope.boundTo(new AccountId(2L)));
        assertThat(bound.hashCode()).isEqualTo(CategoryScope.boundTo(new AccountId(1L)).hashCode());
        assertThat(bound.toString()).contains("account=1");
        assertThat(CategoryScope.global().toString()).contains("global");
        assertThat(CategoryScope.global().isBound()).isFalse();
    }
}
