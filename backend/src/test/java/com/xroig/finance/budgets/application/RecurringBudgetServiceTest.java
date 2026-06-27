package com.xroig.finance.budgets.application;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.budgets.application.port.UpsertRecurrence.AmountInput;
import com.xroig.finance.budgets.application.port.UpsertRecurrence.RecurrenceCommand;
import com.xroig.finance.budgets.domain.CategoryCatalog;
import com.xroig.finance.budgets.domain.CategoryCatalog.CategoryBudgetInfo;
import com.xroig.finance.budgets.domain.MonthsMask;
import com.xroig.finance.budgets.domain.RecurrenceAmount;
import com.xroig.finance.budgets.domain.RecurringBudget;
import com.xroig.finance.budgets.domain.RecurringBudgetRepository;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application tests for {@link RecurringBudgetService} with mocked ports: the get/upsert/delete
 * use cases, the legacy guard order (404 missing, then 400 global/subcategories/month/format)
 * and the create-vs-reconcile branch. The amount-history bookkeeping is the aggregate's
 * (covered in {@code RecurringBudgetTest}).
 */
@ExtendWith(MockitoExtension.class)
class RecurringBudgetServiceTest {

    @Mock private RecurringBudgetRepository recurrences;
    @Mock private CategoryCatalog categories;
    @Mock private RecurringBudgetQueryPort queries;
    @InjectMocks private RecurringBudgetService service;

    @Captor private ArgumentCaptor<RecurringBudget> saved;

    private static final CategoryBudgetInfo LEAF = new CategoryBudgetInfo(new CategoryId(10L), new AccountId(1L), false);

    private static RecurrenceCommand command(List<Integer> months, boolean active, AmountInput... amounts) {
        return new RecurrenceCommand(months, active, List.of(amounts));
    }

    private static AmountInput amountIn(String value, String yearMonth) {
        return new AmountInput(new BigDecimal(value), yearMonth);
    }

    private static RecurringBudgetView someView() {
        return new RecurringBudgetView(10L, List.of(1), true, List.of());
    }

    // ---------- get ----------

    @Test
    void get_returnsTheViewFromTheQueryPort() {
        RecurringBudgetView view = someView();
        when(queries.find(new CategoryId(10L))).thenReturn(Optional.of(view));

        assertThat(service.get(10L)).isSameAs(view);
    }

    @Test
    void get_throwsNotFoundWhenNoRecurrence() {
        when(queries.find(new CategoryId(10L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(10L)).isInstanceOf(NotFoundException.class);
    }

    // ---------- upsert: guards ----------

    @Test
    void upsert_throwsNotFoundWhenCategoryMissing() {
        when(categories.find(new CategoryId(10L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsert(10L, command(List.of(1), true, amountIn("100", "2024-01"))))
                .isInstanceOf(NotFoundException.class);
        verify(recurrences, never()).save(any());
    }

    @Test
    void upsert_throwsValidationForGlobalCategory() {
        when(categories.find(new CategoryId(11L)))
                .thenReturn(Optional.of(new CategoryBudgetInfo(new CategoryId(11L), null, false)));

        assertThatThrownBy(() -> service.upsert(11L, command(List.of(1), true, amountIn("100", "2024-01"))))
                .isInstanceOf(ValidationException.class);
        verify(recurrences, never()).save(any());
    }

    @Test
    void upsert_throwsValidationWhenCategoryHasSubcategories() {
        when(categories.find(new CategoryId(10L)))
                .thenReturn(Optional.of(new CategoryBudgetInfo(new CategoryId(10L), new AccountId(1L), true)));

        assertThatThrownBy(() -> service.upsert(10L, command(List.of(1), true, amountIn("100", "2024-01"))))
                .isInstanceOf(ValidationException.class);
        verify(recurrences, never()).save(any());
    }

    @Test
    void upsert_throwsValidationForInvalidMonth() {
        when(categories.find(new CategoryId(10L))).thenReturn(Optional.of(LEAF));

        assertThatThrownBy(() -> service.upsert(10L, command(List.of(0), true, amountIn("100", "2024-01"))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.upsert(10L, command(List.of(13), true, amountIn("100", "2024-01"))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void upsert_throwsValidationForDuplicateValidoDesde() {
        when(categories.find(new CategoryId(10L))).thenReturn(Optional.of(LEAF));
        when(recurrences.findByCategory(new CategoryId(10L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsert(10L,
                command(List.of(1), true, amountIn("100", "2024-01"), amountIn("120", "2024-01"))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void upsert_throwsValidationForInvalidValidoDesdeFormat() {
        when(categories.find(new CategoryId(10L))).thenReturn(Optional.of(LEAF));

        assertThatThrownBy(() -> service.upsert(10L, command(List.of(1), true, amountIn("100", "01/2024"))))
                .isInstanceOf(ValidationException.class);
        verify(recurrences, never()).save(any());
    }

    // ---------- upsert: create vs reconcile ----------

    @Test
    void upsert_createsNewRecurrenceWhenNoneExists() {
        when(categories.find(new CategoryId(10L))).thenReturn(Optional.of(LEAF));
        when(recurrences.findByCategory(new CategoryId(10L))).thenReturn(Optional.empty());
        when(queries.find(new CategoryId(10L))).thenReturn(Optional.of(someView()));

        service.upsert(10L, command(List.of(1, 12), false, amountIn("100", "2024-01")));

        verify(recurrences).save(saved.capture());
        RecurringBudget created = saved.getValue();
        assertThat(created.id()).isNull();
        assertThat(created.categoryId()).isEqualTo(new CategoryId(10L));
        assertThat(created.isActive()).isFalse();
        assertThat(created.months().toMonths()).containsExactly(1, 12);
        assertThat(created.amounts()).singleElement()
                .satisfies(a -> {
                    assertThat(a.amount()).isEqualTo(Money.of("100"));
                    assertThat(a.from()).isEqualTo(YearMonth.parse("2024-01"));
                });
    }

    @Test
    void upsert_reconcilesTheExistingRecurrence() {
        RecurringBudget existing = RecurringBudget.create(new CategoryId(10L),
                MonthsMask.ofMonths(List.of(1)), true,
                List.of(new RecurrenceAmount(Money.of("100"), YearMonth.parse("2024-01"))));
        when(categories.find(new CategoryId(10L))).thenReturn(Optional.of(LEAF));
        when(recurrences.findByCategory(new CategoryId(10L))).thenReturn(Optional.of(existing));
        when(queries.find(new CategoryId(10L))).thenReturn(Optional.of(someView()));

        service.upsert(10L, command(List.of(2, 5), false, amountIn("150", "2024-01"), amountIn("300", "2024-09")));

        verify(recurrences).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(existing.isActive()).isFalse();
        assertThat(existing.months().toMonths()).containsExactly(2, 5);
        assertThat(existing.amounts()).extracting(a -> a.from().toString())
                .containsExactlyInAnyOrder("2024-01", "2024-09");
    }

    @Test
    void upsert_returnsTheReReadView() {
        RecurringBudgetView view = someView();
        when(categories.find(new CategoryId(10L))).thenReturn(Optional.of(LEAF));
        when(recurrences.findByCategory(new CategoryId(10L))).thenReturn(Optional.empty());
        when(queries.find(new CategoryId(10L))).thenReturn(Optional.of(view));

        assertThat(service.upsert(10L, command(List.of(1), true, amountIn("100", "2024-01")))).isSameAs(view);
    }

    // ---------- delete ----------

    @Test
    void delete_delegatesToTheRepository() {
        service.delete(10L);

        verify(recurrences).deleteByCategory(new CategoryId(10L));
    }
}
