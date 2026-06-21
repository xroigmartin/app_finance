package com.xroig.finance.controller;

import com.xroig.finance.model.Account;
import com.xroig.finance.model.Category;
import com.xroig.finance.model.TransactionType;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.BudgetRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.CategoryRuleRepository;
import com.xroig.finance.repository.RecurringBudgetRepository;
import com.xroig.finance.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static com.xroig.finance.Fixtures.account;
import static com.xroig.finance.Fixtures.category;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CategoryController} with mocked repositories (stage
 * E3a): parent/subcategory resolution, account scope, recurrence guards and
 * deletion rules. HTTP wiring and {@code @Valid} are covered later by MockMvc.
 */
@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private CategoryRuleRepository ruleRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private RecurringBudgetRepository recurringBudgetRepository;
    @InjectMocks private CategoryController controller;

    private final Account corriente = account(1, "Corriente");

    /** A request body category (the nested parent/account carry only their id). */
    private static Category request(String name, TransactionType type, Long parentId, Long accountId) {
        Category c = new Category();
        c.setName(name);
        c.setColor("#64748b");
        c.setType(type);
        if (parentId != null) {
            Category p = new Category();
            p.setId(parentId);
            c.setParent(p);
        }
        if (accountId != null) {
            Account a = new Account();
            a.setId(accountId);
            c.setAccount(a);
        }
        return c;
    }

    private static HttpStatus statusOf(Throwable t) {
        return (HttpStatus) ((ResponseStatusException) t).getStatusCode();
    }

    // ---------- create ----------

    @Test
    void create_topLevelGlobal() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        Category saved = controller.create(request("Comida", TransactionType.EXPENSE, null, null));

        assertThat(saved.getParent()).isNull();
        assertThat(saved.getAccount()).isNull();
        assertThat(saved.getId()).isNull();
    }

    @Test
    void create_topLevelAccountBound() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(corriente));
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        Category saved = controller.create(request("Comida", TransactionType.EXPENSE, null, 1L));

        assertThat(saved.getAccount()).isSameAs(corriente);
    }

    @Test
    void create_invalidAccountThrows400() {
        when(accountRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.create(request("Comida", TransactionType.EXPENSE, null, 9L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_subcategoryInheritsParentTypeAndAccount() {
        Category parent = category(20, "Hogar", TransactionType.EXPENSE, corriente);
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(parent));
        when(recurringBudgetRepository.existsByCategoryId(20L)).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        // The child request carries a contradictory type/account that must be
        // overridden by inheritance from the parent.
        Category saved = controller.create(request("Luz", TransactionType.INCOME, 20L, null));

        assertThat(saved.getParent()).isSameAs(parent);
        assertThat(saved.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(saved.getAccount()).isSameAs(corriente);
    }

    @Test
    void create_subcategoryOfGlobalParentCanBeScopedToAccount() {
        Category parent = category(20, "Hogar", TransactionType.EXPENSE); // global
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(parent));
        when(recurringBudgetRepository.existsByCategoryId(20L)).thenReturn(false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(corriente));
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        Category saved = controller.create(request("Luz", TransactionType.EXPENSE, 20L, 1L));

        assertThat(saved.getAccount()).isSameAs(corriente);
    }

    @Test
    void create_subcategoryWhenParentHasRecurrenceThrows409() {
        Category parent = category(20, "Hogar", TransactionType.EXPENSE, corriente);
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(parent));
        when(recurringBudgetRepository.existsByCategoryId(20L)).thenReturn(true);

        assertThatThrownBy(() -> controller.create(request("Luz", TransactionType.EXPENSE, 20L, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void create_parentThatIsItselfASubcategoryThrows400() {
        Category grandParent = category(5, "Top", TransactionType.EXPENSE);
        Category parent = category(20, "Hogar", TransactionType.EXPENSE);
        parent.setParent(grandParent);
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> controller.create(request("Luz", TransactionType.EXPENSE, 20L, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_parentNotFoundThrows400() {
        when(categoryRepository.findById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.create(request("Luz", TransactionType.EXPENSE, 20L, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ---------- update ----------

    @Test
    void update_notFoundThrows404() {
        when(categoryRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.update(7L, request("X", TransactionType.EXPENSE, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void update_selfAsParentThrows400() {
        Category existing = category(7, "X", TransactionType.EXPENSE);
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> controller.update(7L, request("X", TransactionType.EXPENSE, 7L, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void update_categoryWithSubcategoriesCannotBecomeSubcategory() {
        Category existing = category(7, "X", TransactionType.EXPENSE);
        Category parent = category(20, "Hogar", TransactionType.EXPENSE);
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(parent));
        when(categoryRepository.existsByParentId(7L)).thenReturn(true);

        assertThatThrownBy(() -> controller.update(7L, request("X", TransactionType.EXPENSE, 20L, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void update_assigningAccountWithIncompatibleChildThrows400() {
        Category existing = category(7, "X", TransactionType.EXPENSE);
        Category globalChild = category(8, "Sub", TransactionType.EXPENSE); // global → incompatible
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(corriente));
        when(categoryRepository.findByParentId(7L)).thenReturn(List.of(globalChild));

        assertThatThrownBy(() -> controller.update(7L, request("X", TransactionType.EXPENSE, null, 1L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void update_assigningAccountWithMovementsOfAnotherAccountThrows409() {
        Category existing = category(7, "X", TransactionType.EXPENSE);
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(corriente));
        when(categoryRepository.findByParentId(7L)).thenReturn(List.of());
        when(transactionRepository.existsByCategoryIdAndAccountIdNot(7L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> controller.update(7L, request("X", TransactionType.EXPENSE, null, 1L)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void update_makingGlobalWithRecurrenceThrows409() {
        Category existing = category(7, "X", TransactionType.EXPENSE, corriente);
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(recurringBudgetRepository.existsByCategoryId(7L)).thenReturn(true);

        // No account in the request → becomes global, which conflicts with the recurrence.
        assertThatThrownBy(() -> controller.update(7L, request("X", TransactionType.EXPENSE, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void update_happyPathTopLevelGlobal() {
        Category existing = category(7, "Viejo", TransactionType.EXPENSE, corriente);
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(recurringBudgetRepository.existsByCategoryId(7L)).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        Category updated = controller.update(7L, request("Nuevo", TransactionType.INCOME, null, null));

        assertThat(updated.getName()).isEqualTo("Nuevo");
        assertThat(updated.getColor()).isEqualTo("#64748b");
        assertThat(updated.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(updated.getAccount()).isNull();
        assertThat(updated.getParent()).isNull();
    }

    // ---------- delete ----------

    @Test
    void delete_withSubcategoriesThrows409() {
        when(categoryRepository.existsByParentId(7L)).thenReturn(true);

        assertThatThrownBy(() -> controller.delete(7L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
        verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    void delete_withTransactionsThrows409() {
        when(categoryRepository.existsByParentId(7L)).thenReturn(false);
        when(transactionRepository.existsByCategoryId(7L)).thenReturn(true);

        assertThatThrownBy(() -> controller.delete(7L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void delete_withBudgetThrows409() {
        when(categoryRepository.existsByParentId(7L)).thenReturn(false);
        when(transactionRepository.existsByCategoryId(7L)).thenReturn(false);
        when(budgetRepository.existsByCategoryId(7L)).thenReturn(true);

        assertThatThrownBy(() -> controller.delete(7L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void delete_withRulesThrows409() {
        when(categoryRepository.existsByParentId(7L)).thenReturn(false);
        when(transactionRepository.existsByCategoryId(7L)).thenReturn(false);
        when(budgetRepository.existsByCategoryId(7L)).thenReturn(false);
        when(ruleRepository.existsByCategoryId(7L)).thenReturn(true);

        assertThatThrownBy(() -> controller.delete(7L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void delete_happyPathDeletesById() {
        when(categoryRepository.existsByParentId(7L)).thenReturn(false);
        when(transactionRepository.existsByCategoryId(7L)).thenReturn(false);
        when(budgetRepository.existsByCategoryId(7L)).thenReturn(false);
        when(ruleRepository.existsByCategoryId(7L)).thenReturn(false);

        controller.delete(7L);

        verify(categoryRepository).deleteById(7L);
    }
}
