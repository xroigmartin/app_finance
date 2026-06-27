package com.xroig.finance.budgets.application;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.budgets.application.port.CopyBudgets.CopyCommand;
import com.xroig.finance.budgets.application.port.CreateBudget.BudgetCommand;
import com.xroig.finance.budgets.domain.AccountExistence;
import com.xroig.finance.budgets.domain.Budget;
import com.xroig.finance.budgets.domain.BudgetId;
import com.xroig.finance.budgets.domain.BudgetRepository;
import com.xroig.finance.budgets.domain.CategoryCatalog;
import com.xroig.finance.budgets.domain.CategoryCatalog.CategoryBudgetInfo;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.shared.domain.ConflictException;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application-service tests for {@link BudgetService} with mocked outbound ports
 * (stage H5a): the create/update guard order (duplicate slot, account, category,
 * leaf, scope), the copy use case and the read delegations.
 */
@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    private static final AccountId ACCOUNT = new AccountId(1L);
    private static final CategoryId CATEGORY = new CategoryId(10L);

    @Mock private BudgetRepository budgets;
    @Mock private CategoryCatalog categories;
    @Mock private AccountExistence accounts;
    @Mock private BudgetQueryPort queries;

    private BudgetService service() {
        return new BudgetService(budgets, categories, accounts, queries);
    }

    private static BudgetCommand cmd(long accountId, long categoryId, int year, int month, String amount) {
        return new BudgetCommand(accountId, categoryId, year, month, new BigDecimal(amount));
    }

    private static BudgetView aView() {
        return new BudgetView(99L, null, null, 2024, 3, new BigDecimal("100"));
    }

    /** Makes save assign an id and the read port return a view for it. */
    private void stubSaveAndView() {
        when(budgets.save(any(Budget.class))).thenAnswer(i -> {
            Budget b = i.getArgument(0);
            return Budget.rehydrate(new BudgetId(99L), b.accountId(), b.categoryId(), b.year(), b.month(), b.amount());
        });
        when(queries.findById(new BudgetId(99L))).thenReturn(Optional.of(aView()));
    }

    private void stubLeafCategoryOn(AccountId accountId) {
        when(categories.find(CATEGORY))
                .thenReturn(Optional.of(new CategoryBudgetInfo(CATEGORY, accountId, false)));
        lenient().when(accounts.exists(ACCOUNT)).thenReturn(true);
    }

    // ---------- create ----------

    @Test
    void create_duplicateThrowsConflict() {
        when(budgets.existsAt(ACCOUNT, CATEGORY, 2024, 3)).thenReturn(true);

        assertThatThrownBy(() -> service().create(cmd(1, 10, 2024, 3, "100")))
                .isInstanceOf(ConflictException.class);
        verify(budgets, never()).save(any());
    }

    @Test
    void create_invalidAccountThrowsValidation() {
        when(budgets.existsAt(ACCOUNT, CATEGORY, 2024, 3)).thenReturn(false);
        when(accounts.exists(ACCOUNT)).thenReturn(false);

        assertThatThrownBy(() -> service().create(cmd(1, 10, 2024, 3, "100")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Cuenta no válida");
        verify(budgets, never()).save(any());
    }

    @Test
    void create_invalidCategoryThrowsValidation() {
        when(budgets.existsAt(ACCOUNT, CATEGORY, 2024, 3)).thenReturn(false);
        when(accounts.exists(ACCOUNT)).thenReturn(true);
        when(categories.find(CATEGORY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(cmd(1, 10, 2024, 3, "100")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Categoría no válida");
    }

    @Test
    void create_categoryWithSubcategoriesThrowsValidation() {
        when(budgets.existsAt(ACCOUNT, CATEGORY, 2024, 3)).thenReturn(false);
        when(accounts.exists(ACCOUNT)).thenReturn(true);
        when(categories.find(CATEGORY))
                .thenReturn(Optional.of(new CategoryBudgetInfo(CATEGORY, ACCOUNT, true)));

        assertThatThrownBy(() -> service().create(cmd(1, 10, 2024, 3, "100")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("subcategorías");
    }

    @Test
    void create_categoryOfAnotherAccountThrowsValidation() {
        when(budgets.existsAt(ACCOUNT, CATEGORY, 2024, 3)).thenReturn(false);
        when(accounts.exists(ACCOUNT)).thenReturn(true);
        when(categories.find(CATEGORY))
                .thenReturn(Optional.of(new CategoryBudgetInfo(CATEGORY, new AccountId(2L), false)));

        assertThatThrownBy(() -> service().create(cmd(1, 10, 2024, 3, "100")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("La categoría pertenece a otra cuenta");
    }

    @Test
    void create_globalCategoryIsAllowed() {
        when(budgets.existsAt(ACCOUNT, CATEGORY, 2024, 3)).thenReturn(false);
        when(accounts.exists(ACCOUNT)).thenReturn(true);
        when(categories.find(CATEGORY))
                .thenReturn(Optional.of(new CategoryBudgetInfo(CATEGORY, null, false)));
        stubSaveAndView();

        assertThat(service().create(cmd(1, 10, 2024, 3, "100"))).isEqualTo(aView());
    }

    @Test
    void create_happyPathSavesAndReturnsView() {
        when(budgets.existsAt(ACCOUNT, CATEGORY, 2024, 3)).thenReturn(false);
        stubLeafCategoryOn(ACCOUNT);
        stubSaveAndView();

        BudgetView result = service().create(cmd(1, 10, 2024, 3, "100"));

        assertThat(result).isEqualTo(aView());
        ArgumentCaptor<Budget> saved = ArgumentCaptor.forClass(Budget.class);
        verify(budgets).save(saved.capture());
        assertThat(saved.getValue().month()).isEqualTo(3);
        assertThat(saved.getValue().amount()).isEqualTo(Money.of("100"));
    }

    @Test
    void create_whenSavedBudgetCannotBeReadBack_throwsIllegalState() {
        when(budgets.existsAt(ACCOUNT, CATEGORY, 2024, 3)).thenReturn(false);
        stubLeafCategoryOn(ACCOUNT);
        when(budgets.save(any(Budget.class)))
                .thenReturn(Budget.rehydrate(new BudgetId(99L), ACCOUNT, CATEGORY, 2024, 3, Money.of("100")));
        when(queries.findById(new BudgetId(99L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(cmd(1, 10, 2024, 3, "100")))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- update ----------

    @Test
    void update_notFoundThrows() {
        when(budgets.findById(new BudgetId(5L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(5L, cmd(1, 10, 2024, 3, "100")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_movingToOccupiedSlotThrowsConflict() {
        Budget existing = Budget.rehydrate(new BudgetId(5L), ACCOUNT, CATEGORY, 2024, 2, Money.of("80"));
        when(budgets.findById(new BudgetId(5L))).thenReturn(Optional.of(existing));
        when(budgets.existsAt(ACCOUNT, CATEGORY, 2024, 3)).thenReturn(true);

        assertThatThrownBy(() -> service().update(5L, cmd(1, 10, 2024, 3, "100")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_samePlaceSkipsDuplicateCheckAndSaves() {
        Budget existing = Budget.rehydrate(new BudgetId(5L), ACCOUNT, CATEGORY, 2024, 3, Money.of("80"));
        when(budgets.findById(new BudgetId(5L))).thenReturn(Optional.of(existing));
        stubLeafCategoryOn(ACCOUNT);
        when(budgets.save(any(Budget.class))).thenReturn(existing);
        when(queries.findById(new BudgetId(5L))).thenReturn(Optional.of(aView()));

        service().update(5L, cmd(1, 10, 2024, 3, "150"));

        verify(budgets, never()).existsAt(any(), any(), anyInt(), anyInt());
        assertThat(existing.amount()).isEqualTo(Money.of("150"));
    }

    // ---------- delete ----------

    @Test
    void delete_delegatesToRepository() {
        service().delete(5L);
        verify(budgets).deleteById(new BudgetId(5L));
    }

    // ---------- copy ----------

    @Test
    void copy_skipsCategoriesAlreadyBudgetedInTarget() {
        Budget comida = Budget.rehydrate(new BudgetId(1L), ACCOUNT, new CategoryId(10L), 2024, 1, Money.of("100"));
        Budget ocio = Budget.rehydrate(new BudgetId(2L), ACCOUNT, new CategoryId(11L), 2024, 1, Money.of("60"));
        when(budgets.findByYearMonth(2024, 1)).thenReturn(List.of(comida, ocio));
        when(budgets.existsAt(ACCOUNT, new CategoryId(10L), 2024, 2)).thenReturn(true);  // comida occupied
        when(budgets.existsAt(ACCOUNT, new CategoryId(11L), 2024, 2)).thenReturn(false);
        when(budgets.save(any(Budget.class))).thenAnswer(i -> {
            Budget b = i.getArgument(0);
            return Budget.rehydrate(new BudgetId(99L), b.accountId(), b.categoryId(), b.year(), b.month(), b.amount());
        });
        when(queries.findById(new BudgetId(99L))).thenReturn(Optional.of(aView()));

        List<BudgetView> copied = service().copy(new CopyCommand(2024, 1, 2024, 2));

        assertThat(copied).hasSize(1);
        ArgumentCaptor<Budget> saved = ArgumentCaptor.forClass(Budget.class);
        verify(budgets).save(saved.capture());
        assertThat(saved.getValue().categoryId()).isEqualTo(new CategoryId(11L));
        assertThat(saved.getValue().month()).isEqualTo(2);
        assertThat(saved.getValue().amount()).isEqualTo(Money.of("60"));
    }

    // ---------- reads ----------

    @Test
    void find_delegatesToQueryPort() {
        when(queries.find(2024, 3, 1L)).thenReturn(List.of(aView()));
        assertThat(service().find(2024, 3, 1L)).containsExactly(aView());
    }

    @Test
    void annual_delegatesToQueryPort() {
        AnnualBudgetView expected = new AnnualBudgetView(2024, 1L, List.of(), List.of());
        when(queries.annual(2024, 1L)).thenReturn(expected);
        assertThat(service().annual(2024, 1L)).isSameAs(expected);
    }
}
