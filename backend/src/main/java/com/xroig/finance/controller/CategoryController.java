package com.xroig.finance.controller;

import com.xroig.finance.model.Account;
import com.xroig.finance.model.Category;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.BudgetRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.CategoryRuleRepository;
import com.xroig.finance.repository.RecurringBudgetRepository;
import com.xroig.finance.repository.TransactionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final CategoryRuleRepository ruleRepository;
    private final AccountRepository accountRepository;
    private final RecurringBudgetRepository recurringBudgetRepository;

    public CategoryController(CategoryRepository categoryRepository,
                              TransactionRepository transactionRepository,
                              BudgetRepository budgetRepository,
                              CategoryRuleRepository ruleRepository,
                              AccountRepository accountRepository,
                              RecurringBudgetRepository recurringBudgetRepository) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.ruleRepository = ruleRepository;
        this.accountRepository = accountRepository;
        this.recurringBudgetRepository = recurringBudgetRepository;
    }

    @GetMapping
    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Category create(@Valid @RequestBody Category category) {
        category.setId(null);
        Category parent = resolveParent(category, null);
        if (parent != null) {
            // Adding a child turns the parent into a non-leaf category, which is
            // incompatible with it having a recurrence.
            if (recurringBudgetRepository.existsByCategoryId(parent.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La categoría principal tiene una recurrencia; quítala antes de añadirle subcategorías");
            }
            // Subcategories inherit their parent's type; the scope is inherited
            // only when the parent is account-bound (see resolveSubAccount).
            category.setParent(parent);
            category.setType(parent.getType());
            category.setAccount(resolveSubAccount(category, parent));
        } else {
            category.setParent(null);
            category.setAccount(resolveAccount(category));
        }
        return categoryRepository.save(category);
    }

    @PutMapping("/{id}")
    public Category update(@PathVariable Long id, @Valid @RequestBody Category category) {
        Category existing = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));
        Category parent = resolveParent(category, id);
        Account account;
        if (parent != null) {
            if (categoryRepository.existsByParentId(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Una categoría con subcategorías no puede convertirse en subcategoría");
            }
            existing.setParent(parent);
            existing.setType(parent.getType());
            account = resolveSubAccount(category, parent);
        } else {
            existing.setParent(null);
            existing.setType(category.getType());
            account = resolveAccount(category);
            // A top-level category can only become account-bound if every
            // subcategory already belongs to that same account; otherwise its
            // global / cross-account children would be left orphaned.
            if (account != null) {
                Long accountId = account.getId();
                boolean incompatibleChild = categoryRepository.findByParentId(id).stream()
                        .anyMatch(child -> child.getAccount() == null
                                || !child.getAccount().getId().equals(accountId));
                if (incompatibleChild) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "No se puede asignar esta categoría a una cuenta: tiene subcategorías "
                                    + "globales o de otra cuenta. Cámbialas primero.");
                }
            }
        }
        if (account != null
                && transactionRepository.existsByCategoryIdAndAccountIdNot(id, account.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La categoría tiene movimientos de otra cuenta y no puede asignarse a esta");
        }
        // A recurrence only makes sense on an account-bound category; making it
        // global would orphan it.
        if (account == null && recurringBudgetRepository.existsByCategoryId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La categoría tiene una recurrencia; quítala antes de hacerla global");
        }
        existing.setName(category.getName());
        existing.setColor(category.getColor());
        existing.setAccount(account);
        return categoryRepository.save(existing);
    }

    /**
     * Resolves a subcategory's owning account. An account-bound parent forces
     * its children into the same account; a global parent lets each child be
     * global or scoped to any account (taken from the request).
     */
    private Account resolveSubAccount(Category category, Category parent) {
        if (parent.getAccount() != null) {
            return parent.getAccount();
        }
        return resolveAccount(category);
    }

    /** Resolves the (optional) owning account sent as a nested {@code {id}}. */
    private Account resolveAccount(Category category) {
        if (category.getAccount() == null || category.getAccount().getId() == null) {
            return null;
        }
        return accountRepository.findById(category.getAccount().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cuenta no válida"));
    }

    /**
     * Resolves the (optional) parent sent as a nested {@code {id}}, enforcing
     * the single-level rule: the parent must exist, must be top-level, and
     * cannot be the category itself.
     */
    private Category resolveParent(Category category, Long selfId) {
        if (category.getParent() == null || category.getParent().getId() == null) {
            return null;
        }
        Long parentId = category.getParent().getId();
        if (parentId.equals(selfId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Una categoría no puede ser su propia categoría principal");
        }
        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoría principal no válida"));
        if (parent.getParent() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se permite un nivel de subcategorías");
        }
        return parent;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (categoryRepository.existsByParentId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La categoría tiene subcategorías y no se puede eliminar");
        }
        if (transactionRepository.existsByCategoryId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La categoría tiene movimientos asociados y no se puede eliminar");
        }
        if (budgetRepository.existsByCategoryId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La categoría tiene un presupuesto asociado y no se puede eliminar");
        }
        if (ruleRepository.existsByCategoryId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La categoría tiene reglas de categorización asociadas y no se puede eliminar");
        }
        categoryRepository.deleteById(id);
    }
}
