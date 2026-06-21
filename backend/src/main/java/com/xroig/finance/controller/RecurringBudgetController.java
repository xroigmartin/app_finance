package com.xroig.finance.controller;

import com.xroig.finance.dto.RecurringBudgetDtos.RecurringBudgetRequest;
import com.xroig.finance.dto.RecurringBudgetDtos.RecurringBudgetResponse;
import com.xroig.finance.service.RecurringBudgetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Recurrence of a category, as a sub-resource so that everything lives in the
 * category form. Only leaf, account-bound categories may have a recurrence
 * (validated in {@link RecurringBudgetService}).
 */
@RestController
@RequestMapping("/api/categories/{categoryId}/recurrence")
public class RecurringBudgetController {

    private final RecurringBudgetService recurringBudgetService;

    public RecurringBudgetController(RecurringBudgetService recurringBudgetService) {
        this.recurringBudgetService = recurringBudgetService;
    }

    @GetMapping
    public RecurringBudgetResponse get(@PathVariable Long categoryId) {
        return recurringBudgetService.get(categoryId);
    }

    @PutMapping
    public RecurringBudgetResponse upsert(@PathVariable Long categoryId,
                                          @Valid @RequestBody RecurringBudgetRequest request) {
        return recurringBudgetService.upsert(categoryId, request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long categoryId) {
        recurringBudgetService.delete(categoryId);
    }
}
