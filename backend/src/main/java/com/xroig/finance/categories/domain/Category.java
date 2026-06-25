package com.xroig.finance.categories.domain;

import com.xroig.finance.model.TransactionType;
import com.xroig.finance.shared.domain.ValidationException;

import java.util.Objects;

/**
 * Category aggregate root, pure of any framework or persistence concern.
 *
 * <p>A category is either top-level or a subcategory of a top-level one — only
 * <b>one</b> level of nesting is allowed. It references its (optional) parent by
 * identity ({@link CategoryId}) and its scope ({@link CategoryScope}) references
 * the owning account by {@link com.xroig.finance.accounts.domain.AccountId}; the
 * aggregate never navigates to those objects.
 *
 * <p>Invariants kept here: name and type are mandatory; a subcategory inherits its
 * parent's <i>type</i> and may only hang off a top-level parent. The account-scope
 * inheritance of a subcategory (a bound parent forces its account; a global parent
 * lets the child choose) is decided by the application service, because it gates a
 * cross-aggregate account validation — see {@code CategoryService}.
 *
 * <p>{@code type} uses the legacy {@link TransactionType} enum (a pure value, no
 * framework): it is shared-kernel material that will move to {@code shared/domain}
 * when transactions migrate (H3).
 */
public class Category {

    private static final String DEFAULT_COLOR = "#6366f1";

    private final CategoryId id;
    private String name;
    private TransactionType type;
    private String color;
    private CategoryScope scope;
    private CategoryId parentId; // null ⇒ top-level

    private Category(CategoryId id, String name, TransactionType type, String color,
                     CategoryScope scope, CategoryId parentId) {
        this.id = id;
        this.name = requireName(name);
        this.type = requireType(type);
        this.color = normalizeColor(color);
        this.scope = Objects.requireNonNull(scope, "scope");
        this.parentId = parentId;
    }

    /** Factory for a brand-new top-level category. */
    public static Category createTopLevel(String name, TransactionType type, String color, CategoryScope scope) {
        return new Category(null, name, type, color, scope, null);
    }

    /**
     * Factory for a brand-new subcategory of {@code parent}. Enforces the single-level
     * rule and inherits the parent's type; the {@code scope} is the one already
     * resolved by the caller (account-bound parents force their account).
     */
    public static Category createSubcategory(String name, String color, CategoryScope scope, Category parent) {
        requireTopLevelParent(parent);
        return new Category(null, name, parent.type, color, scope, parent.id);
    }

    /** Rebuilds a persisted category (identity present), from the persistence adapter. */
    public static Category rehydrate(CategoryId id, String name, TransactionType type, String color,
                                     CategoryScope scope, CategoryId parentId) {
        if (id == null) {
            throw new IllegalArgumentException("Una categoría rehidratada necesita identidad");
        }
        return new Category(id, name, type, color, scope, parentId);
    }

    /** Turns this category into (or keeps it as) a top-level one with the given attributes. */
    public void makeTopLevel(String name, TransactionType type, String color, CategoryScope scope) {
        this.parentId = null;
        this.type = requireType(type);
        this.scope = Objects.requireNonNull(scope, "scope");
        this.name = requireName(name);
        this.color = normalizeColor(color);
    }

    /** Converts this category into a subcategory of {@code parent}, inheriting its type. */
    public void makeSubcategoryOf(Category parent, CategoryScope scope, String name, String color) {
        requireTopLevelParent(parent);
        this.parentId = parent.id;
        this.type = parent.type;
        this.scope = Objects.requireNonNull(scope, "scope");
        this.name = requireName(name);
        this.color = normalizeColor(color);
    }

    public boolean isTopLevel() {
        return parentId == null;
    }

    public boolean isGlobalScope() {
        return scope.isGlobal();
    }

    public CategoryId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public TransactionType type() {
        return type;
    }

    public String color() {
        return color;
    }

    public CategoryScope scope() {
        return scope;
    }

    public CategoryId parentId() {
        return parentId;
    }

    private static void requireTopLevelParent(Category parent) {
        Objects.requireNonNull(parent, "parent");
        if (!parent.isTopLevel()) {
            throw new ValidationException("Solo se permite un nivel de subcategorías");
        }
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("El nombre de la categoría es obligatorio");
        }
        return name;
    }

    private static TransactionType requireType(TransactionType type) {
        if (type == null) {
            throw new ValidationException("El tipo de la categoría es obligatorio");
        }
        return type;
    }

    private static String normalizeColor(String color) {
        return color == null || color.isBlank() ? DEFAULT_COLOR : color;
    }
}
