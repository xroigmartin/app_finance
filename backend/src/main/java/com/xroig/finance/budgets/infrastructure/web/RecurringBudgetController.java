package com.xroig.finance.budgets.infrastructure.web;

import com.xroig.finance.budgets.application.RecurringBudgetView;
import com.xroig.finance.budgets.application.port.DeleteRecurrence;
import com.xroig.finance.budgets.application.port.FindRecurrence;
import com.xroig.finance.budgets.application.port.UpsertRecurrence;
import com.xroig.finance.budgets.application.port.UpsertRecurrence.AmountInput;
import com.xroig.finance.budgets.application.port.UpsertRecurrence.RecurrenceCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recurrence of a category, as a sub-resource so everything lives in the category form. Only
 * leaf, account-bound categories may have one (validated in the application service). Thin
 * inbound adapter: it (de)serializes DTOs and delegates to the inbound ports.
 */
@RestController
@RequestMapping("/api/categories/{categoryId}/recurrence")
public class RecurringBudgetController {

    private final FindRecurrence findRecurrence;
    private final UpsertRecurrence upsertRecurrence;
    private final DeleteRecurrence deleteRecurrence;

    public RecurringBudgetController(FindRecurrence findRecurrence, UpsertRecurrence upsertRecurrence,
                                     DeleteRecurrence deleteRecurrence) {
        this.findRecurrence = findRecurrence;
        this.upsertRecurrence = upsertRecurrence;
        this.deleteRecurrence = deleteRecurrence;
    }

    @GetMapping
    public RecurringBudgetView get(@PathVariable Long categoryId) {
        return findRecurrence.get(categoryId);
    }

    @PutMapping
    public RecurringBudgetView upsert(@PathVariable Long categoryId,
                                      @Valid @RequestBody RecurringBudgetRequest request) {
        return upsertRecurrence.upsert(categoryId, toCommand(request));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long categoryId) {
        deleteRecurrence.delete(categoryId);
    }

    private static RecurrenceCommand toCommand(RecurringBudgetRequest r) {
        return new RecurrenceCommand(r.months(), r.active(),
                r.amounts().stream().map(a -> new AmountInput(a.amount(), a.validoDesde())).toList());
    }
}
