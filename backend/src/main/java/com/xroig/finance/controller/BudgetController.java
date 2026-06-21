package com.xroig.finance.controller;

import com.xroig.finance.model.Account;
import com.xroig.finance.model.Budget;
import com.xroig.finance.model.Category;
import com.xroig.finance.dto.BudgetDtos.AnnualBudget;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.BudgetRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.service.BudgetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    public record BudgetRequest(@NotNull Long accountId,
                                @NotNull Long categoryId,
                                @NotNull Integer year,
                                @NotNull @Min(1) @Max(12) Integer month,
                                @NotNull @Positive BigDecimal amount) {
    }

    public record CopyRequest(@NotNull Integer fromYear,
                              @NotNull @Min(1) @Max(12) Integer fromMonth,
                              @NotNull Integer toYear,
                              @NotNull @Min(1) @Max(12) Integer toMonth) {
    }

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final BudgetService budgetService;

    public BudgetController(BudgetRepository budgetRepository,
                            CategoryRepository categoryRepository,
                            AccountRepository accountRepository,
                            BudgetService budgetService) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.budgetService = budgetService;
    }

    @GetMapping
    public List<Budget> find(@RequestParam(required = false) Integer year,
                             @RequestParam(required = false) Integer month,
                             @RequestParam(required = false) Long accountId) {
        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();
        return accountId != null
                ? budgetRepository.findByAccountIdAndYearAndMonth(accountId, y, m)
                : budgetRepository.findByYearAndMonth(y, m);
    }

    /** Annual matrix (12 months × categories) of planned vs. real amounts. */
    @GetMapping("/annual")
    public AnnualBudget annual(@RequestParam(required = false) Integer year,
                               @RequestParam(required = false) Long accountId) {
        int y = year != null ? year : LocalDate.now().getYear();
        return budgetService.annual(y, accountId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Budget create(@Valid @RequestBody BudgetRequest request) {
        if (budgetRepository.existsByAccountIdAndCategoryIdAndYearAndMonth(
                request.accountId(), request.categoryId(), request.year(), request.month())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La categoría ya tiene presupuesto en ese mes para esta cuenta");
        }
        Budget budget = new Budget();
        apply(budget, request);
        return budgetRepository.save(budget);
    }

    @PutMapping("/{id}")
    public Budget update(@PathVariable Long id, @Valid @RequestBody BudgetRequest request) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Presupuesto no encontrado"));
        boolean samePlace = budget.getAccount().getId().equals(request.accountId())
                && budget.getCategory().getId().equals(request.categoryId())
                && budget.getYear().equals(request.year())
                && budget.getMonth().equals(request.month());
        if (!samePlace && budgetRepository.existsByAccountIdAndCategoryIdAndYearAndMonth(
                request.accountId(), request.categoryId(), request.year(), request.month())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La categoría ya tiene presupuesto en ese mes para esta cuenta");
        }
        apply(budget, request);
        return budgetRepository.save(budget);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        budgetRepository.deleteById(id);
    }

    /**
     * Copies the budgets of one month into another, skipping categories
     * that already have a budget in the target month.
     */
    @PostMapping("/copy")
    public List<Budget> copy(@Valid @RequestBody CopyRequest request) {
        List<Budget> copied = budgetRepository
                .findByYearAndMonth(request.fromYear(), request.fromMonth()).stream()
                .filter(b -> !budgetRepository.existsByAccountIdAndCategoryIdAndYearAndMonth(
                        b.getAccount().getId(), b.getCategory().getId(),
                        request.toYear(), request.toMonth()))
                .map(b -> {
                    Budget copy = new Budget();
                    copy.setAccount(b.getAccount());
                    copy.setCategory(b.getCategory());
                    copy.setYear(request.toYear());
                    copy.setMonth(request.toMonth());
                    copy.setAmount(b.getAmount());
                    return copy;
                })
                .toList();
        return budgetRepository.saveAll(copied);
    }

    private void apply(Budget budget, BudgetRequest request) {
        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cuenta no válida"));
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoría no válida"));
        if (categoryRepository.existsByParentId(category.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se puede presupuestar una categoría con subcategorías; presupuesta sus subcategorías");
        }
        if (category.getAccount() != null && !category.getAccount().getId().equals(account.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La categoría pertenece a otra cuenta");
        }
        budget.setAccount(account);
        budget.setCategory(category);
        budget.setYear(request.year());
        budget.setMonth(request.month());
        budget.setAmount(request.amount());
    }
}
