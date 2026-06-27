package com.xroig.finance.budgets.infrastructure.web;

import com.xroig.finance.budgets.application.AnnualBudgetView;
import com.xroig.finance.budgets.application.BudgetView;
import com.xroig.finance.budgets.application.port.CopyBudgets;
import com.xroig.finance.budgets.application.port.CopyBudgets.CopyCommand;
import com.xroig.finance.budgets.application.port.CreateBudget;
import com.xroig.finance.budgets.application.port.CreateBudget.BudgetCommand;
import com.xroig.finance.budgets.application.port.DeleteBudget;
import com.xroig.finance.budgets.application.port.FindBudgets;
import com.xroig.finance.budgets.application.port.UpdateBudget;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Inbound web adapter for the budgets context. Thin: it (de)serializes DTOs, resolves
 * the current-date fallbacks and delegates to the inbound ports, returning the
 * {@link BudgetView}/{@link AnnualBudgetView} read models.
 */
@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final FindBudgets findBudgets;
    private final CreateBudget createBudget;
    private final UpdateBudget updateBudget;
    private final DeleteBudget deleteBudget;
    private final CopyBudgets copyBudgets;

    public BudgetController(FindBudgets findBudgets, CreateBudget createBudget, UpdateBudget updateBudget,
                           DeleteBudget deleteBudget, CopyBudgets copyBudgets) {
        this.findBudgets = findBudgets;
        this.createBudget = createBudget;
        this.updateBudget = updateBudget;
        this.deleteBudget = deleteBudget;
        this.copyBudgets = copyBudgets;
    }

    @GetMapping
    public List<BudgetView> find(@RequestParam(required = false) Integer year,
                                 @RequestParam(required = false) Integer month,
                                 @RequestParam(required = false) Long accountId) {
        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();
        return findBudgets.find(y, m, accountId);
    }

    /** Annual matrix (12 months × categories) of planned vs. real amounts. */
    @GetMapping("/annual")
    public AnnualBudgetView annual(@RequestParam(required = false) Integer year,
                                   @RequestParam(required = false) Long accountId) {
        int y = year != null ? year : LocalDate.now().getYear();
        return findBudgets.annual(y, accountId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetView create(@Valid @RequestBody BudgetRequest request) {
        return createBudget.create(toCommand(request));
    }

    @PutMapping("/{id}")
    public BudgetView update(@PathVariable Long id, @Valid @RequestBody BudgetRequest request) {
        return updateBudget.update(id, toCommand(request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deleteBudget.delete(id);
    }

    @PostMapping("/copy")
    public List<BudgetView> copy(@Valid @RequestBody CopyRequest request) {
        return copyBudgets.copy(new CopyCommand(
                request.fromYear(), request.fromMonth(), request.toYear(), request.toMonth()));
    }

    private static BudgetCommand toCommand(BudgetRequest r) {
        return new BudgetCommand(r.accountId(), r.categoryId(), r.year(), r.month(), r.amount());
    }
}
