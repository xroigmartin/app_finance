package com.xroig.finance.categories.application;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.application.port.CreateCategory.CreateCategoryCommand;
import com.xroig.finance.categories.application.port.UpdateCategory.UpdateCategoryCommand;
import com.xroig.finance.categories.domain.AccountExistence;
import com.xroig.finance.categories.domain.Category;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categories.domain.CategoryReferences;
import com.xroig.finance.categories.domain.CategoryRepository;
import com.xroig.finance.categories.domain.CategoryScope;
import com.xroig.finance.model.TransactionType;
import com.xroig.finance.shared.domain.ConflictException;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application-service tests for the categories use cases with the outbound ports
 * mocked. They reproduce the branch logic the legacy {@code CategoryControllerTest}
 * pinned (parent/subcategory resolution, scope rules, recurrence/scope guards and the
 * deletion guards) — now against the service, with the invariants themselves living
 * in {@code CategoryTest}.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final AccountId ACC = new AccountId(1L);
    private static final CategoryView ANY_VIEW =
            new CategoryView(100L, "x", TransactionType.EXPENSE, "#000", null, null);

    @Mock private CategoryRepository categories;
    @Mock private CategoryReferences references;
    @Mock private AccountExistence accounts;
    @Mock private CategoryQueryPort queries;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categories, references, accounts, queries);
    }

    /** A persisted top-level category at the given scope. */
    private static Category top(long id, TransactionType type, CategoryScope scope) {
        return Category.rehydrate(new CategoryId(id), "C" + id, type, "#000", scope, null);
    }

    private void stubSaveAndView() {
        when(categories.save(any())).thenAnswer(i -> {
            Category c = i.getArgument(0);
            return Category.rehydrate(new CategoryId(100L), c.name(), c.type(), c.color(), c.scope(), c.parentId());
        });
        when(queries.findById(new CategoryId(100L))).thenReturn(Optional.of(ANY_VIEW));
    }

    private Category captureSaved() {
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categories).save(captor.capture());
        return captor.getValue();
    }

    // ---------- read ----------

    @Test
    void all_delegatesToTheQueryPort() {
        when(queries.findAll()).thenReturn(List.of(ANY_VIEW));

        assertThat(service.all()).containsExactly(ANY_VIEW);
    }

    @Test
    void create_whenSavedViewCannotBeRead_failsLoudly() {
        when(categories.save(any())).thenAnswer(i -> {
            Category c = i.getArgument(0);
            return Category.rehydrate(new CategoryId(100L), c.name(), c.type(), c.color(), c.scope(), c.parentId());
        });
        when(queries.findById(new CategoryId(100L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                new CreateCategoryCommand("Comida", TransactionType.EXPENSE, "#000", null, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- create ----------

    @Test
    void create_topLevelGlobal() {
        stubSaveAndView();

        service.create(new CreateCategoryCommand("Comida", TransactionType.EXPENSE, "#000", null, null));

        Category saved = captureSaved();
        assertThat(saved.isTopLevel()).isTrue();
        assertThat(saved.isGlobalScope()).isTrue();
    }

    @Test
    void create_topLevelAccountBound_validatesAccount() {
        when(accounts.exists(ACC)).thenReturn(true);
        stubSaveAndView();

        service.create(new CreateCategoryCommand("Comida", TransactionType.EXPENSE, "#000", null, 1L));

        assertThat(captureSaved().scope()).isEqualTo(CategoryScope.boundTo(ACC));
    }

    @Test
    void create_invalidAccountThrows() {
        when(accounts.exists(ACC)).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                new CreateCategoryCommand("Comida", TransactionType.EXPENSE, "#000", null, 1L)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Cuenta no válida");
        verify(categories, never()).save(any());
    }

    @Test
    void create_subcategoryInheritsParentTypeAndAccount() {
        when(categories.findById(new CategoryId(20L)))
                .thenReturn(Optional.of(top(20, TransactionType.EXPENSE, CategoryScope.boundTo(ACC))));
        when(references.hasRecurrence(new CategoryId(20L))).thenReturn(false);
        stubSaveAndView();

        // Contradictory type/account in the request are overridden by inheritance.
        service.create(new CreateCategoryCommand("Luz", TransactionType.INCOME, "#000", 20L, null));

        Category saved = captureSaved();
        assertThat(saved.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(saved.scope()).isEqualTo(CategoryScope.boundTo(ACC));
        assertThat(saved.parentId()).isEqualTo(new CategoryId(20L));
    }

    @Test
    void create_subcategoryOfGlobalParentCanBeScopedToAccount() {
        when(categories.findById(new CategoryId(20L)))
                .thenReturn(Optional.of(top(20, TransactionType.EXPENSE, CategoryScope.global())));
        when(references.hasRecurrence(new CategoryId(20L))).thenReturn(false);
        when(accounts.exists(ACC)).thenReturn(true);
        stubSaveAndView();

        service.create(new CreateCategoryCommand("Luz", TransactionType.EXPENSE, "#000", 20L, 1L));

        assertThat(captureSaved().scope()).isEqualTo(CategoryScope.boundTo(ACC));
    }

    @Test
    void create_subcategoryOfGlobalParentStaysGlobalWhenNoAccount() {
        when(categories.findById(new CategoryId(20L)))
                .thenReturn(Optional.of(top(20, TransactionType.EXPENSE, CategoryScope.global())));
        when(references.hasRecurrence(new CategoryId(20L))).thenReturn(false);
        stubSaveAndView();

        service.create(new CreateCategoryCommand("Luz", TransactionType.EXPENSE, "#000", 20L, null));

        assertThat(captureSaved().isGlobalScope()).isTrue();
    }

    @Test
    void create_subcategoryWhenParentHasRecurrenceThrows() {
        when(categories.findById(new CategoryId(20L)))
                .thenReturn(Optional.of(top(20, TransactionType.EXPENSE, CategoryScope.boundTo(ACC))));
        when(references.hasRecurrence(new CategoryId(20L))).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateCategoryCommand("Luz", TransactionType.EXPENSE, "#000", 20L, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("recurrencia");
        verify(categories, never()).save(any());
    }

    @Test
    void create_parentThatIsItselfASubcategoryThrows() {
        Category subParent = Category.rehydrate(new CategoryId(20L), "Hogar", TransactionType.EXPENSE,
                "#000", CategoryScope.global(), new CategoryId(5L));
        when(categories.findById(new CategoryId(20L))).thenReturn(Optional.of(subParent));

        assertThatThrownBy(() -> service.create(
                new CreateCategoryCommand("Luz", TransactionType.EXPENSE, "#000", 20L, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("un nivel");
    }

    @Test
    void create_parentNotFoundThrows() {
        when(categories.findById(new CategoryId(20L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                new CreateCategoryCommand("Luz", TransactionType.EXPENSE, "#000", 20L, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Categoría principal no válida");
    }

    // ---------- update ----------

    @Test
    void update_notFoundThrows() {
        when(categories.findById(new CategoryId(7L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(7L,
                new UpdateCategoryCommand("X", TransactionType.EXPENSE, "#000", null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_selfAsParentThrows() {
        when(categories.findById(new CategoryId(7L)))
                .thenReturn(Optional.of(top(7, TransactionType.EXPENSE, CategoryScope.global())));

        assertThatThrownBy(() -> service.update(7L,
                new UpdateCategoryCommand("X", TransactionType.EXPENSE, "#000", 7L, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("su propia categoría principal");
    }

    @Test
    void update_categoryWithSubcategoriesCannotBecomeSubcategory() {
        when(categories.findById(new CategoryId(7L)))
                .thenReturn(Optional.of(top(7, TransactionType.EXPENSE, CategoryScope.global())));
        when(categories.findById(new CategoryId(20L)))
                .thenReturn(Optional.of(top(20, TransactionType.EXPENSE, CategoryScope.global())));
        when(categories.existsByParentId(new CategoryId(7L))).thenReturn(true);

        assertThatThrownBy(() -> service.update(7L,
                new UpdateCategoryCommand("X", TransactionType.EXPENSE, "#000", 20L, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no puede convertirse en subcategoría");
    }

    @Test
    void update_assigningAccountWithIncompatibleChildThrows() {
        when(categories.findById(new CategoryId(7L)))
                .thenReturn(Optional.of(top(7, TransactionType.EXPENSE, CategoryScope.global())));
        when(accounts.exists(ACC)).thenReturn(true);
        Category globalChild = Category.rehydrate(new CategoryId(8L), "Sub", TransactionType.EXPENSE,
                "#000", CategoryScope.global(), new CategoryId(7L));
        when(categories.findChildren(new CategoryId(7L))).thenReturn(List.of(globalChild));

        assertThatThrownBy(() -> service.update(7L,
                new UpdateCategoryCommand("X", TransactionType.EXPENSE, "#000", null, 1L)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("globales o de otra cuenta");
    }

    @Test
    void update_assigningAccountWithMovementsOfAnotherAccountThrows() {
        when(categories.findById(new CategoryId(7L)))
                .thenReturn(Optional.of(top(7, TransactionType.EXPENSE, CategoryScope.global())));
        when(accounts.exists(ACC)).thenReturn(true);
        when(categories.findChildren(new CategoryId(7L))).thenReturn(List.of());
        when(references.hasTransactionsOnOtherAccount(new CategoryId(7L), ACC)).thenReturn(true);

        assertThatThrownBy(() -> service.update(7L,
                new UpdateCategoryCommand("X", TransactionType.EXPENSE, "#000", null, 1L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("movimientos de otra cuenta");
    }

    @Test
    void update_makingGlobalWithRecurrenceThrows() {
        when(categories.findById(new CategoryId(7L)))
                .thenReturn(Optional.of(top(7, TransactionType.EXPENSE, CategoryScope.boundTo(ACC))));
        when(references.hasRecurrence(new CategoryId(7L))).thenReturn(true);

        assertThatThrownBy(() -> service.update(7L,
                new UpdateCategoryCommand("X", TransactionType.EXPENSE, "#000", null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("recurrencia");
    }

    @Test
    void update_convertingToSubcategoryInheritsParentTypeAndScope() {
        when(categories.findById(new CategoryId(7L)))
                .thenReturn(Optional.of(top(7, TransactionType.INCOME, CategoryScope.global())));
        when(categories.findById(new CategoryId(20L)))
                .thenReturn(Optional.of(top(20, TransactionType.EXPENSE, CategoryScope.boundTo(ACC))));
        when(categories.existsByParentId(new CategoryId(7L))).thenReturn(false);
        when(references.hasTransactionsOnOtherAccount(new CategoryId(7L), ACC)).thenReturn(false);
        stubSaveAndView();

        service.update(7L, new UpdateCategoryCommand("X", TransactionType.INCOME, "#000", 20L, null));

        Category saved = captureSaved();
        assertThat(saved.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(saved.scope()).isEqualTo(CategoryScope.boundTo(ACC));
        assertThat(saved.parentId()).isEqualTo(new CategoryId(20L));
    }

    @Test
    void update_assigningAccountWithCompatibleChildSucceeds() {
        when(categories.findById(new CategoryId(7L)))
                .thenReturn(Optional.of(top(7, TransactionType.EXPENSE, CategoryScope.global())));
        when(accounts.exists(ACC)).thenReturn(true);
        Category sameAccountChild = Category.rehydrate(new CategoryId(8L), "Sub", TransactionType.EXPENSE,
                "#000", CategoryScope.boundTo(ACC), new CategoryId(7L));
        when(categories.findChildren(new CategoryId(7L))).thenReturn(List.of(sameAccountChild));
        when(references.hasTransactionsOnOtherAccount(new CategoryId(7L), ACC)).thenReturn(false);
        stubSaveAndView();

        service.update(7L, new UpdateCategoryCommand("X", TransactionType.EXPENSE, "#000", null, 1L));

        assertThat(captureSaved().scope()).isEqualTo(CategoryScope.boundTo(ACC));
    }

    @Test
    void update_happyPathTopLevelGlobal() {
        when(categories.findById(new CategoryId(7L)))
                .thenReturn(Optional.of(top(7, TransactionType.EXPENSE, CategoryScope.boundTo(ACC))));
        when(references.hasRecurrence(new CategoryId(7L))).thenReturn(false);
        stubSaveAndView();

        service.update(7L, new UpdateCategoryCommand("Nuevo", TransactionType.INCOME, "#abc", null, null));

        Category saved = captureSaved();
        assertThat(saved.name()).isEqualTo("Nuevo");
        assertThat(saved.color()).isEqualTo("#abc");
        assertThat(saved.type()).isEqualTo(TransactionType.INCOME);
        assertThat(saved.isGlobalScope()).isTrue();
        assertThat(saved.isTopLevel()).isTrue();
    }

    // ---------- delete ----------

    @Test
    void delete_withSubcategoriesThrows() {
        when(categories.existsByParentId(new CategoryId(7L))).thenReturn(true);

        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("subcategorías");
        verify(categories, never()).deleteById(any());
    }

    @Test
    void delete_withTransactionsThrows() {
        when(categories.existsByParentId(new CategoryId(7L))).thenReturn(false);
        when(references.hasTransactions(new CategoryId(7L))).thenReturn(true);

        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("movimientos");
    }

    @Test
    void delete_withBudgetThrows() {
        when(categories.existsByParentId(new CategoryId(7L))).thenReturn(false);
        when(references.hasTransactions(new CategoryId(7L))).thenReturn(false);
        when(references.hasBudget(new CategoryId(7L))).thenReturn(true);

        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("presupuesto");
    }

    @Test
    void delete_withRulesThrows() {
        when(categories.existsByParentId(new CategoryId(7L))).thenReturn(false);
        when(references.hasTransactions(new CategoryId(7L))).thenReturn(false);
        when(references.hasBudget(new CategoryId(7L))).thenReturn(false);
        when(references.hasRule(new CategoryId(7L))).thenReturn(true);

        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("reglas");
    }

    @Test
    void delete_happyPathDeletesById() {
        lenient().when(categories.existsByParentId(new CategoryId(7L))).thenReturn(false);
        lenient().when(references.hasTransactions(new CategoryId(7L))).thenReturn(false);
        lenient().when(references.hasBudget(new CategoryId(7L))).thenReturn(false);
        lenient().when(references.hasRule(new CategoryId(7L))).thenReturn(false);

        service.delete(7L);

        verify(categories).deleteById(new CategoryId(7L));
    }
}
