package com.xroig.finance.service;

import com.xroig.finance.dto.RecurringBudgetDtos.AmountRequest;
import com.xroig.finance.dto.RecurringBudgetDtos.AmountResponse;
import com.xroig.finance.dto.RecurringBudgetDtos.RecurringBudgetRequest;
import com.xroig.finance.dto.RecurringBudgetDtos.RecurringBudgetResponse;
import com.xroig.finance.model.Category;
import com.xroig.finance.model.RecurringBudget;
import com.xroig.finance.model.RecurringBudgetAmount;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.RecurringBudgetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the recurrence attached to a leaf, account-bound category. The
 * recurrence is a generator of planned (budget) amounts; it never creates real
 * movements. See {@link BudgetService} for how it is merged into the matrix.
 */
@Service
@Transactional(readOnly = true)
public class RecurringBudgetService {

    private final RecurringBudgetRepository recurringRepository;
    private final CategoryRepository categoryRepository;

    public RecurringBudgetService(RecurringBudgetRepository recurringRepository,
                                  CategoryRepository categoryRepository) {
        this.recurringRepository = recurringRepository;
        this.categoryRepository = categoryRepository;
    }

    public RecurringBudgetResponse get(Long categoryId) {
        RecurringBudget recurrence = recurringRepository.findByCategoryIdWithAmounts(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "La categoría no tiene recurrencia"));
        return toResponse(recurrence);
    }

    /** Creates or replaces the recurrence of a category. */
    @Transactional
    public RecurringBudgetResponse upsert(Long categoryId, RecurringBudgetRequest request) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));
        if (category.getAccount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las categorías globales no admiten recurrencia; crea una subcategoría ligada a una cuenta");
        }
        if (categoryRepository.existsByParentId(categoryId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se puede definir recurrencia en una categoría con subcategorías; defínela en sus subcategorías");
        }
        int months = toBitmask(request.months());
        List<RecurringBudgetAmount> amounts = parseAmounts(request.amounts());

        RecurringBudget recurrence = recurringRepository.findByCategoryIdWithAmounts(categoryId)
                .orElseGet(() -> {
                    RecurringBudget r = new RecurringBudget();
                    r.setCategory(category);
                    return r;
                });
        recurrence.setMonths(months);
        recurrence.setActive(request.active());

        // Reconcile amounts in place keyed by validoDesde: update the rows that
        // stay, add the new ones and drop the rest. Clearing and reinserting
        // instead would make Hibernate insert a row with the same
        // (recurring_budget_id, valido_desde) before deleting the old one
        // (inserts flush before deletes), violating uq_amount_vigencia — the
        // exact case of editing an amount while keeping its effective month.
        Map<LocalDate, RecurringBudgetAmount> existing = recurrence.getAmounts().stream()
                .collect(Collectors.toMap(RecurringBudgetAmount::getValidoDesde, a -> a));
        Set<LocalDate> wanted = new HashSet<>();
        for (RecurringBudgetAmount parsed : amounts) {
            LocalDate from = parsed.getValidoDesde();
            wanted.add(from);
            RecurringBudgetAmount row = existing.get(from);
            if (row != null) {
                row.setAmount(parsed.getAmount());
            } else {
                parsed.setRecurringBudget(recurrence);
                recurrence.getAmounts().add(parsed);
            }
        }
        recurrence.getAmounts().removeIf(a -> !wanted.contains(a.getValidoDesde()));
        return toResponse(recurringRepository.save(recurrence));
    }

    @Transactional
    public void delete(Long categoryId) {
        recurringRepository.findByCategoryIdWithAmounts(categoryId)
                .ifPresent(recurringRepository::delete);
    }

    private List<RecurringBudgetAmount> parseAmounts(List<AmountRequest> requested) {
        Set<YearMonth> seen = new HashSet<>();
        List<RecurringBudgetAmount> result = new ArrayList<>();
        for (AmountRequest req : requested) {
            YearMonth ym = parseYearMonth(req.validoDesde());
            if (!seen.add(ym)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Hay dos importes con la misma fecha de vigencia (" + req.validoDesde() + ")");
            }
            RecurringBudgetAmount amount = new RecurringBudgetAmount();
            amount.setAmount(req.amount());
            amount.setValidoDesde(ym.atDay(1));
            result.add(amount);
        }
        return result;
    }

    private static YearMonth parseYearMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Fecha de vigencia no válida (formato esperado AAAA-MM): " + value);
        }
    }

    private static int toBitmask(List<Integer> months) {
        int mask = 0;
        for (Integer m : months) {
            if (m == null || m < 1 || m > 12) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mes no válido: " + m);
            }
            mask |= 1 << (m - 1);
        }
        return mask;
    }

    private static List<Integer> fromBitmask(int mask) {
        List<Integer> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            if ((mask & (1 << (m - 1))) != 0) {
                months.add(m);
            }
        }
        return months;
    }

    private static RecurringBudgetResponse toResponse(RecurringBudget recurrence) {
        List<AmountResponse> amounts = recurrence.getAmounts().stream()
                .sorted(Comparator.comparing(RecurringBudgetAmount::getValidoDesde))
                .map(a -> new AmountResponse(a.getId(), a.getAmount(),
                        YearMonth.from(a.getValidoDesde()).toString()))
                .toList();
        return new RecurringBudgetResponse(recurrence.getCategory().getId(),
                fromBitmask(recurrence.getMonths()), recurrence.isActive(), amounts);
    }
}
