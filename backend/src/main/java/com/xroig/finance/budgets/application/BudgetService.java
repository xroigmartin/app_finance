package com.xroig.finance.budgets.application;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.budgets.application.port.CopyBudgets;
import com.xroig.finance.budgets.application.port.CreateBudget;
import com.xroig.finance.budgets.application.port.DeleteBudget;
import com.xroig.finance.budgets.application.port.FindBudgets;
import com.xroig.finance.budgets.application.port.UpdateBudget;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Application service for the budgets context. Orchestrates the {@link Budget}
 * aggregate and the outbound ports, preserving the legacy guard order: on create
 * the duplicate-slot check fires first (409), then the account/category/leaf/scope
 * checks (400); on update the not-found check (404) and the same-slot shortcut come
 * before the duplicate check.
 */
@Service
@Transactional
public class BudgetService implements FindBudgets, CreateBudget, UpdateBudget, DeleteBudget, CopyBudgets {

    private final BudgetRepository budgets;
    private final CategoryCatalog categories;
    private final AccountExistence accounts;
    private final BudgetQueryPort queries;

    public BudgetService(BudgetRepository budgets, CategoryCatalog categories,
                         AccountExistence accounts, BudgetQueryPort queries) {
        this.budgets = budgets;
        this.categories = categories;
        this.accounts = accounts;
        this.queries = queries;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BudgetView> find(int year, int month, Long accountId) {
        return queries.find(year, month, accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public AnnualBudgetView annual(int year, Long accountId) {
        return queries.annual(year, accountId);
    }

    @Override
    public BudgetView create(BudgetCommand command) {
        AccountId account = new AccountId(command.accountId());
        CategoryId category = new CategoryId(command.categoryId());
        if (budgets.existsAt(account, category, command.year(), command.month())) {
            throw new ConflictException("La categoría ya tiene presupuesto en ese mes para esta cuenta");
        }
        requireBudgetable(account, category);
        Budget budget = Budget.create(account, category, command.year(), command.month(), Money.of(command.amount()));
        return view(budgets.save(budget).id());
    }

    @Override
    public BudgetView update(long id, BudgetCommand command) {
        Budget existing = budgets.findById(new BudgetId(id))
                .orElseThrow(() -> new NotFoundException("Presupuesto no encontrado"));
        AccountId account = new AccountId(command.accountId());
        CategoryId category = new CategoryId(command.categoryId());
        boolean samePlace = existing.isAt(account, category, command.year(), command.month());
        if (!samePlace && budgets.existsAt(account, category, command.year(), command.month())) {
            throw new ConflictException("La categoría ya tiene presupuesto en ese mes para esta cuenta");
        }
        requireBudgetable(account, category);
        existing.reassign(account, category, command.year(), command.month(), Money.of(command.amount()));
        return view(budgets.save(existing).id());
    }

    @Override
    public void delete(long id) {
        budgets.deleteById(new BudgetId(id));
    }

    @Override
    public List<BudgetView> copy(CopyCommand command) {
        List<BudgetView> copied = new ArrayList<>();
        for (Budget source : budgets.findByYearMonth(command.fromYear(), command.fromMonth())) {
            if (!budgets.existsAt(source.accountId(), source.categoryId(), command.toYear(), command.toMonth())) {
                Budget copy = source.copyTo(command.toYear(), command.toMonth());
                copied.add(view(budgets.save(copy).id()));
            }
        }
        return copied;
    }

    /** Cross-aggregate guards in the legacy order: account, then category, then leaf, then scope. */
    private void requireBudgetable(AccountId account, CategoryId category) {
        if (!accounts.exists(account)) {
            throw new ValidationException("Cuenta no válida");
        }
        CategoryBudgetInfo info = categories.find(category)
                .orElseThrow(() -> new ValidationException("Categoría no válida"));
        if (info.hasChildren()) {
            throw new ValidationException(
                    "No se puede presupuestar una categoría con subcategorías; presupuesta sus subcategorías");
        }
        if (!info.isGlobal() && !info.accountId().equals(account)) {
            throw new ValidationException("La categoría pertenece a otra cuenta");
        }
    }

    private BudgetView view(BudgetId id) {
        return queries.findById(id).orElseThrow(
                () -> new IllegalStateException("El presupuesto recién guardado no se pudo leer: " + id.value()));
    }
}
