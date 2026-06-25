package com.xroig.finance.controller;

import com.xroig.finance.controller.BudgetController.BudgetRequest;
import com.xroig.finance.controller.BudgetController.CopyRequest;
import com.xroig.finance.dto.BudgetDtos.AnnualBudget;
import com.xroig.finance.model.Account;
import com.xroig.finance.model.Budget;
import com.xroig.finance.model.Category;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.BudgetRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.service.BudgetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.xroig.finance.Fixtures.account;
import static com.xroig.finance.Fixtures.budget;
import static com.xroig.finance.Fixtures.category;
import static com.xroig.finance.Fixtures.eur;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BudgetController} with mocked repositories and service
 * (stage E3c): duplicate guard, leaf-category rule, account scope, the copy
 * endpoint and the find/annual delegations.
 */
@ExtendWith(MockitoExtension.class)
class BudgetControllerTest {

    @Mock private BudgetRepository budgetRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private BudgetService budgetService;
    @InjectMocks private BudgetController controller;

    private final Account corriente = account(1, "Corriente");
    private final Account ahorro = account(2, "Ahorro");
    private final Category comida = category(10, "Comida", TransactionType.EXPENSE);

    private BudgetRequest req(Long accountId, Long categoryId, int year, int month, String amount) {
        return new BudgetRequest(accountId, categoryId, year, month, eur(amount));
    }

    private static HttpStatus statusOf(Throwable t) {
        return (HttpStatus) ((ResponseStatusException) t).getStatusCode();
    }

    // ---------- create ----------

    @Test
    void create_duplicateThrows409() {
        when(budgetRepository.existsByAccountIdAndCategoryIdAndYearAndMonth(1L, 10L, 2024, 3))
                .thenReturn(true);

        assertThatThrownBy(() -> controller.create(req(1L, 10L, 2024, 3, "100")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void create_invalidAccountThrows400() {
        when(budgetRepository.existsByAccountIdAndCategoryIdAndYearAndMonth(1L, 10L, 2024, 3))
                .thenReturn(false);
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.create(req(1L, 10L, 2024, 3, "100")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_invalidCategoryThrows400() {
        when(budgetRepository.existsByAccountIdAndCategoryIdAndYearAndMonth(1L, 10L, 2024, 3))
                .thenReturn(false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(corriente));
        when(categoryRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.create(req(1L, 10L, 2024, 3, "100")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_categoryWithSubcategoriesThrows400() {
        when(budgetRepository.existsByAccountIdAndCategoryIdAndYearAndMonth(1L, 10L, 2024, 3))
                .thenReturn(false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(corriente));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(comida));
        when(categoryRepository.existsByParentId(10L)).thenReturn(true);

        assertThatThrownBy(() -> controller.create(req(1L, 10L, 2024, 3, "100")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_categoryOfAnotherAccountThrows400() {
        Category ajena = category(10, "Comida", TransactionType.EXPENSE, ahorro);
        when(budgetRepository.existsByAccountIdAndCategoryIdAndYearAndMonth(1L, 10L, 2024, 3))
                .thenReturn(false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(corriente));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(ajena));
        when(categoryRepository.existsByParentId(10L)).thenReturn(false);

        assertThatThrownBy(() -> controller.create(req(1L, 10L, 2024, 3, "100")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_happyPathSavesBudget() {
        when(budgetRepository.existsByAccountIdAndCategoryIdAndYearAndMonth(1L, 10L, 2024, 3))
                .thenReturn(false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(corriente));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(comida));
        when(categoryRepository.existsByParentId(10L)).thenReturn(false);
        when(budgetRepository.save(any(Budget.class))).thenAnswer(i -> i.getArgument(0));

        Budget saved = controller.create(req(1L, 10L, 2024, 3, "100"));

        assertThat(saved.getAccount()).isSameAs(corriente);
        assertThat(saved.getCategory()).isSameAs(comida);
        assertThat(saved.getYear()).isEqualTo(2024);
        assertThat(saved.getMonth()).isEqualTo(3);
        assertThat(saved.getAmount()).isEqualByComparingTo("100");
    }

    // ---------- update ----------

    @Test
    void update_notFoundThrows404() {
        when(budgetRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.update(5L, req(1L, 10L, 2024, 3, "100")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void update_movingToOccupiedSlotThrows409() {
        Budget existing = budget(5L, corriente, comida, 2024, 2, eur("80")); // month 2
        when(budgetRepository.findById(5L)).thenReturn(Optional.of(existing));
        // request targets month 3, which is occupied
        when(budgetRepository.existsByAccountIdAndCategoryIdAndYearAndMonth(1L, 10L, 2024, 3))
                .thenReturn(true);

        assertThatThrownBy(() -> controller.update(5L, req(1L, 10L, 2024, 3, "100")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void update_samePlaceSkipsDuplicateCheckAndSaves() {
        Budget existing = budget(5L, corriente, comida, 2024, 3, eur("80"));
        when(budgetRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(corriente));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(comida));
        when(categoryRepository.existsByParentId(10L)).thenReturn(false);
        when(budgetRepository.save(any(Budget.class))).thenAnswer(i -> i.getArgument(0));

        // Same account/category/year/month → the duplicate guard must not fire.
        Budget updated = controller.update(5L, req(1L, 10L, 2024, 3, "150"));

        assertThat(updated.getAmount()).isEqualByComparingTo("150");
        verify(budgetRepository, never())
                .existsByAccountIdAndCategoryIdAndYearAndMonth(any(), any(), any(), any());
    }

    // ---------- copy ----------

    @Test
    void copy_skipsCategoriesAlreadyBudgetedInTarget() {
        Category ocio = category(11, "Ocio", TransactionType.EXPENSE);
        Budget b1 = budget(1L, corriente, comida, 2024, 1, eur("100"));
        Budget b2 = budget(2L, corriente, ocio, 2024, 1, eur("60"));
        when(budgetRepository.findByYearAndMonth(2024, 1)).thenReturn(List.of(b1, b2));
        when(budgetRepository.existsByAccountIdAndCategoryIdAndYearAndMonth(1L, 10L, 2024, 2))
                .thenReturn(true); // comida already budgeted in target
        when(budgetRepository.existsByAccountIdAndCategoryIdAndYearAndMonth(1L, 11L, 2024, 2))
                .thenReturn(false);
        when(budgetRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        List<Budget> copied = controller.copy(new CopyRequest(2024, 1, 2024, 2));

        assertThat(copied).singleElement().satisfies(b -> {
            assertThat(b.getCategory()).isSameAs(ocio);
            assertThat(b.getYear()).isEqualTo(2024);
            assertThat(b.getMonth()).isEqualTo(2);
            assertThat(b.getAmount()).isEqualByComparingTo("60");
        });
    }

    @Test
    void copy_copiesAllWhenTargetIsEmpty() {
        Budget b1 = budget(1L, corriente, comida, 2024, 1, eur("100"));
        when(budgetRepository.findByYearAndMonth(2024, 1)).thenReturn(List.of(b1));
        when(budgetRepository.existsByAccountIdAndCategoryIdAndYearAndMonth(1L, 10L, 2024, 2))
                .thenReturn(false);
        when(budgetRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        List<Budget> copied = controller.copy(new CopyRequest(2024, 1, 2024, 2));

        assertThat(copied).hasSize(1);
        assertThat(copied.get(0).getMonth()).isEqualTo(2);
    }

    // ---------- delete / find / annual ----------

    @Test
    void delete_delegatesToRepository() {
        controller.delete(5L);
        verify(budgetRepository).deleteById(5L);
    }

    @Test
    void find_withAccountUsesAccountScopedQuery() {
        controller.find(2024, 3, 1L);
        verify(budgetRepository).findByAccountIdAndYearAndMonth(1L, 2024, 3);
    }

    @Test
    void find_withoutDatesDefaultsToCurrentMonth() {
        LocalDate now = LocalDate.now();
        controller.find(null, null, null);
        verify(budgetRepository).findByYearAndMonth(now.getYear(), now.getMonthValue());
    }

    @Test
    void annual_delegatesToServiceWithDefaultYear() {
        int year = LocalDate.now().getYear();
        AnnualBudget expected = new AnnualBudget(year, 1L, List.of(), List.of());
        when(budgetService.annual(year, 1L)).thenReturn(expected);

        assertThat(controller.annual(null, 1L)).isSameAs(expected);
    }
}
