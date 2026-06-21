package com.xroig.finance.service;

import com.xroig.finance.dto.RecurringBudgetDtos.AmountRequest;
import com.xroig.finance.dto.RecurringBudgetDtos.RecurringBudgetRequest;
import com.xroig.finance.dto.RecurringBudgetDtos.RecurringBudgetResponse;
import com.xroig.finance.model.Account;
import com.xroig.finance.model.Category;
import com.xroig.finance.model.RecurringBudget;
import com.xroig.finance.model.RecurringBudgetAmount;
import com.xroig.finance.model.TransactionType;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.RecurringBudgetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.xroig.finance.Fixtures.account;
import static com.xroig.finance.Fixtures.amount;
import static com.xroig.finance.Fixtures.category;
import static com.xroig.finance.Fixtures.eur;
import static com.xroig.finance.Fixtures.recurrence;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service tests for {@link RecurringBudgetService} with mocked repositories
 * (stage E2a): upsert/reconciliation, parseAmounts, bitmask, get/delete.
 */
@ExtendWith(MockitoExtension.class)
class RecurringBudgetServiceTest {

    @Mock
    private RecurringBudgetRepository recurringRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private RecurringBudgetService service;

    private final Account cuenta = account(1, "Corriente");
    private final Category gasto = category(10, "Comunidad", TransactionType.EXPENSE, cuenta);

    private RecurringBudgetRequest request(List<Integer> months, boolean active, AmountRequest... amounts) {
        return new RecurringBudgetRequest(months, active, List.of(amounts));
    }

    private AmountRequest amountReq(String value, String yearMonth) {
        return new AmountRequest(eur(value), yearMonth);
    }

    private static HttpStatus statusOf(Throwable t) {
        return (HttpStatus) ((ResponseStatusException) t).getStatusCode();
    }

    // ---------- get ----------

    @Test
    void get_returnsResponseMappingMaskAmountsAndActive() {
        RecurringBudget rec = recurrence(gasto, 0b1000_0000_0001, true,
                amount(eur("100.00"), "2024-01"), amount(eur("120.00"), "2024-06"));
        when(recurringRepository.findByCategoryIdWithAmounts(10L)).thenReturn(Optional.of(rec));

        RecurringBudgetResponse resp = service.get(10L);

        assertThat(resp.categoryId()).isEqualTo(10L);
        assertThat(resp.active()).isTrue();
        assertThat(resp.months()).containsExactly(1, 12);
        assertThat(resp.amounts()).extracting("validoDesde").containsExactly("2024-01", "2024-06");
    }

    @Test
    void get_amountsAreSortedByValidoDesde() {
        RecurringBudget rec = recurrence(gasto, 0b1, true,
                amount(eur("200.00"), "2024-06"), amount(eur("100.00"), "2024-01"));
        when(recurringRepository.findByCategoryIdWithAmounts(10L)).thenReturn(Optional.of(rec));

        RecurringBudgetResponse resp = service.get(10L);

        assertThat(resp.amounts()).extracting("validoDesde").containsExactly("2024-01", "2024-06");
    }

    @Test
    void get_throws404WhenNoRecurrence() {
        when(recurringRepository.findByCategoryIdWithAmounts(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---------- upsert: validations ----------

    @Test
    void upsert_throws404WhenCategoryMissing() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsert(10L, request(List.of(1), true, amountReq("100", "2024-01"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void upsert_throws400ForGlobalCategory() {
        Category global = category(11, "Otros", TransactionType.EXPENSE);
        when(categoryRepository.findById(11L)).thenReturn(Optional.of(global));

        assertThatThrownBy(() -> service.upsert(11L, request(List.of(1), true, amountReq("100", "2024-01"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void upsert_throws400WhenCategoryHasSubcategories() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(gasto));
        when(categoryRepository.existsByParentId(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.upsert(10L, request(List.of(1), true, amountReq("100", "2024-01"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void upsert_throws400ForInvalidMonth() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(gasto));
        when(categoryRepository.existsByParentId(10L)).thenReturn(false);

        assertThatThrownBy(() -> service.upsert(10L, request(List.of(0), true, amountReq("100", "2024-01"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.upsert(10L, request(List.of(13), true, amountReq("100", "2024-01"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void upsert_throws400ForDuplicateValidoDesde() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(gasto));
        when(categoryRepository.existsByParentId(10L)).thenReturn(false);

        assertThatThrownBy(() -> service.upsert(10L,
                request(List.of(1), true, amountReq("100", "2024-01"), amountReq("120", "2024-01"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void upsert_throws400ForInvalidValidoDesdeFormat() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(gasto));
        when(categoryRepository.existsByParentId(10L)).thenReturn(false);

        assertThatThrownBy(() -> service.upsert(10L,
                request(List.of(1), true, amountReq("100", "01/2024"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ---------- upsert: create ----------

    @Test
    void upsert_createsNewRecurrenceWhenNoneExists() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(gasto));
        when(categoryRepository.existsByParentId(10L)).thenReturn(false);
        when(recurringRepository.findByCategoryIdWithAmounts(10L)).thenReturn(Optional.empty());
        when(recurringRepository.save(any(RecurringBudget.class))).thenAnswer(i -> i.getArgument(0));

        RecurringBudgetResponse resp = service.upsert(10L,
                request(List.of(1, 12), false, amountReq("100", "2024-01")));

        assertThat(resp.categoryId()).isEqualTo(10L);
        assertThat(resp.active()).isFalse();
        assertThat(resp.months()).containsExactly(1, 12);
        assertThat(resp.amounts()).hasSize(1);
        assertThat(resp.amounts().get(0).amount()).isEqualByComparingTo("100");
        assertThat(resp.amounts().get(0).validoDesde()).isEqualTo("2024-01");
    }

    @Test
    void upsert_normalizesValidoDesdeToFirstDayOfMonth() {
        ArgumentCapturingSave capture = new ArgumentCapturingSave();
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(gasto));
        when(categoryRepository.existsByParentId(10L)).thenReturn(false);
        when(recurringRepository.findByCategoryIdWithAmounts(10L)).thenReturn(Optional.empty());
        when(recurringRepository.save(any(RecurringBudget.class))).thenAnswer(i -> capture.save(i.getArgument(0)));

        service.upsert(10L, request(List.of(3), true, amountReq("100", "2024-03")));

        assertThat(capture.saved.getAmounts())
                .extracting(RecurringBudgetAmount::getValidoDesde)
                .containsExactly(LocalDate.of(2024, 3, 1));
    }

    // ---------- upsert: reconcile ----------

    @Test
    void upsert_reconcilesAmountsKeepingUpdatingAndDropping() {
        RecurringBudget existing = recurrence(gasto, 0b1, true,
                amount(eur("100.00"), "2024-01"), amount(eur("200.00"), "2024-06"));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(gasto));
        when(categoryRepository.existsByParentId(10L)).thenReturn(false);
        when(recurringRepository.findByCategoryIdWithAmounts(10L)).thenReturn(Optional.of(existing));
        when(recurringRepository.save(any(RecurringBudget.class))).thenAnswer(i -> i.getArgument(0));

        // 2024-01 stays (new amount), 2024-06 dropped, 2024-09 added.
        RecurringBudgetResponse resp = service.upsert(10L,
                request(List.of(2, 5), true,
                        amountReq("150", "2024-01"), amountReq("300", "2024-09")));

        assertThat(resp.months()).containsExactly(2, 5);
        assertThat(resp.amounts()).extracting("validoDesde").containsExactly("2024-01", "2024-09");
        assertThat(resp.amounts().get(0).amount()).isEqualByComparingTo("150");
        assertThat(resp.amounts().get(1).amount()).isEqualByComparingTo("300");
    }

    @Test
    void upsert_keepsExistingAmountRowInstanceWhenValidoDesdeUnchanged() {
        RecurringBudgetAmount kept = amount(eur("100.00"), "2024-01");
        RecurringBudget existing = recurrence(gasto, 0b1, true, kept);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(gasto));
        when(categoryRepository.existsByParentId(10L)).thenReturn(false);
        when(recurringRepository.findByCategoryIdWithAmounts(10L)).thenReturn(Optional.of(existing));
        when(recurringRepository.save(any(RecurringBudget.class))).thenAnswer(i -> i.getArgument(0));

        service.upsert(10L, request(List.of(1), true, amountReq("180", "2024-01")));

        // The same row instance is mutated (not replaced) to avoid the
        // insert-before-delete collision against uq_amount_vigencia.
        assertThat(existing.getAmounts()).containsExactly(kept);
        assertThat(kept.getAmount()).isEqualByComparingTo("180");
    }

    // ---------- delete ----------

    @Test
    void delete_removesExistingRecurrence() {
        RecurringBudget rec = recurrence(gasto, 0b1, true, amount(eur("100.00"), "2024-01"));
        when(recurringRepository.findByCategoryIdWithAmounts(10L)).thenReturn(Optional.of(rec));

        service.delete(10L);

        verify(recurringRepository).delete(rec);
    }

    @Test
    void delete_isNoOpWhenAbsent() {
        when(recurringRepository.findByCategoryIdWithAmounts(10L)).thenReturn(Optional.empty());

        service.delete(10L);

        verify(recurringRepository, never()).delete(any());
    }

    /** Tiny helper to capture the entity passed to save() while returning it. */
    private static final class ArgumentCapturingSave {
        private RecurringBudget saved;

        RecurringBudget save(RecurringBudget r) {
            this.saved = r;
            return r;
        }
    }
}
