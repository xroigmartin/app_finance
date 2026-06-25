package com.xroig.finance.categories.application;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.application.port.CreateCategory;
import com.xroig.finance.categories.application.port.DeleteCategory;
import com.xroig.finance.categories.application.port.FindCategories;
import com.xroig.finance.categories.application.port.UpdateCategory;
import com.xroig.finance.categories.domain.AccountExistence;
import com.xroig.finance.categories.domain.Category;
import com.xroig.finance.categories.domain.CategoryId;
import com.xroig.finance.categories.domain.CategoryReferences;
import com.xroig.finance.categories.domain.CategoryRepository;
import com.xroig.finance.categories.domain.CategoryScope;
import com.xroig.finance.shared.domain.ConflictException;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application service for the categories context. Implements the use cases by
 * orchestrating the {@link Category} aggregate and the outbound ports, preserving
 * the exact rule order the legacy {@code CategoryController} ran (so a given input
 * yields the same status). Pure invariants (type inheritance, single-level) live in
 * the aggregate; the cross-aggregate guards (parent existence, account validity,
 * recurrence, movements on other accounts, deletion references) live here.
 */
@Service
@Transactional
public class CategoryService implements FindCategories, CreateCategory, UpdateCategory, DeleteCategory {

    private final CategoryRepository categories;
    private final CategoryReferences references;
    private final AccountExistence accounts;
    private final CategoryQueryPort queries;

    public CategoryService(CategoryRepository categories, CategoryReferences references,
                           AccountExistence accounts, CategoryQueryPort queries) {
        this.categories = categories;
        this.references = references;
        this.accounts = accounts;
        this.queries = queries;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryView> all() {
        return queries.findAll();
    }

    @Override
    public CategoryView create(CreateCategoryCommand command) {
        Category saved;
        if (command.parentId() != null) {
            Category parent = loadTopLevelParent(command.parentId());
            if (references.hasRecurrence(parent.id())) {
                throw new ConflictException(
                        "La categoría principal tiene una recurrencia; quítala antes de añadirle subcategorías");
            }
            CategoryScope scope = subcategoryScope(parent, command.accountId());
            saved = categories.save(Category.createSubcategory(command.name(), command.color(), scope, parent));
        } else {
            CategoryScope scope = resolveScope(command.accountId());
            saved = categories.save(
                    Category.createTopLevel(command.name(), command.type(), command.color(), scope));
        }
        return view(saved.id());
    }

    @Override
    public CategoryView update(long id, UpdateCategoryCommand command) {
        CategoryId categoryId = new CategoryId(id);
        Category existing = categories.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Categoría no encontrada"));

        CategoryScope scope;
        Category parent = null;
        if (command.parentId() != null) {
            if (command.parentId().equals(id)) {
                throw new ValidationException("Una categoría no puede ser su propia categoría principal");
            }
            parent = loadTopLevelParent(command.parentId());
            if (categories.existsByParentId(categoryId)) {
                throw new ValidationException(
                        "Una categoría con subcategorías no puede convertirse en subcategoría");
            }
            scope = subcategoryScope(parent, command.accountId());
        } else {
            scope = resolveScope(command.accountId());
            if (scope.isBound()) {
                // A bound top-level category can only keep children that share its
                // exact scope; a global or other-account child would be orphaned.
                boolean incompatibleChild = categories.findChildren(categoryId).stream()
                        .anyMatch(child -> !child.scope().equals(scope));
                if (incompatibleChild) {
                    throw new ValidationException(
                            "No se puede asignar esta categoría a una cuenta: tiene subcategorías "
                                    + "globales o de otra cuenta. Cámbialas primero.");
                }
            }
        }

        if (scope.isBound()
                && references.hasTransactionsOnOtherAccount(categoryId, scope.accountId().orElseThrow())) {
            throw new ConflictException(
                    "La categoría tiene movimientos de otra cuenta y no puede asignarse a esta");
        }
        if (scope.isGlobal() && references.hasRecurrence(categoryId)) {
            throw new ConflictException(
                    "La categoría tiene una recurrencia; quítala antes de hacerla global");
        }

        if (parent != null) {
            existing.makeSubcategoryOf(parent, scope, command.name(), command.color());
        } else {
            existing.makeTopLevel(command.name(), command.type(), command.color(), scope);
        }
        Category saved = categories.save(existing);
        return view(saved.id());
    }

    @Override
    public void delete(long id) {
        CategoryId categoryId = new CategoryId(id);
        if (categories.existsByParentId(categoryId)) {
            throw new ConflictException("La categoría tiene subcategorías y no se puede eliminar");
        }
        if (references.hasTransactions(categoryId)) {
            throw new ConflictException("La categoría tiene movimientos asociados y no se puede eliminar");
        }
        if (references.hasBudget(categoryId)) {
            throw new ConflictException("La categoría tiene un presupuesto asociado y no se puede eliminar");
        }
        if (references.hasRule(categoryId)) {
            throw new ConflictException(
                    "La categoría tiene reglas de categorización asociadas y no se puede eliminar");
        }
        categories.deleteById(categoryId);
    }

    /** Loads the requested parent, enforcing that it exists and is itself top-level. */
    private Category loadTopLevelParent(Long parentId) {
        Category parent = categories.findById(new CategoryId(parentId))
                .orElseThrow(() -> new ValidationException("Categoría principal no válida"));
        if (!parent.isTopLevel()) {
            throw new ValidationException("Solo se permite un nivel de subcategorías");
        }
        return parent;
    }

    /**
     * The scope of a subcategory: an account-bound parent forces its account (the
     * request's account is ignored, exactly as before); a global parent lets the
     * child pick a scope from the request (validating the account if any).
     */
    private CategoryScope subcategoryScope(Category parent, Long requestedAccountId) {
        return parent.isGlobalScope() ? resolveScope(requestedAccountId) : parent.scope();
    }

    /** Turns an optional account id from the request into a scope, validating it exists. */
    private CategoryScope resolveScope(Long accountId) {
        if (accountId == null) {
            return CategoryScope.global();
        }
        AccountId id = new AccountId(accountId);
        if (!accounts.exists(id)) {
            throw new ValidationException("Cuenta no válida");
        }
        return CategoryScope.boundTo(id);
    }

    private CategoryView view(CategoryId id) {
        return queries.findById(id).orElseThrow(
                () -> new IllegalStateException("La categoría recién guardada no se pudo leer: " + id.value()));
    }
}
