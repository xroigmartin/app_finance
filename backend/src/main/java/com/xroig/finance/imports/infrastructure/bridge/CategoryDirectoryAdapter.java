package com.xroig.finance.imports.infrastructure.bridge;

import com.xroig.finance.categories.application.CategoryQueryPort;
import com.xroig.finance.categories.application.CategoryView;
import com.xroig.finance.categories.application.port.CreateCategory;
import com.xroig.finance.categories.application.port.CreateCategory.CreateCategoryCommand;
import com.xroig.finance.imports.domain.CategoryDirectory;
import com.xroig.finance.shared.domain.TransactionType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bridges {@link CategoryDirectory} to the categories context: lists via the read-side
 * {@link CategoryQueryPort} and creates unknown categories (always global, color
 * {@code #64748b}, no parent) through the {@link CreateCategory} use case.
 */
@Component
class CategoryDirectoryAdapter implements CategoryDirectory {

    private static final String DEFAULT_COLOR = "#64748b";

    private final CategoryQueryPort queries;
    private final CreateCategory createCategory;

    CategoryDirectoryAdapter(CategoryQueryPort queries, CreateCategory createCategory) {
        this.queries = queries;
        this.createCategory = createCategory;
    }

    @Override
    public List<ImportCategory> all() {
        return queries.findAll().stream().map(CategoryDirectoryAdapter::toImportCategory).toList();
    }

    @Override
    public ImportCategory createGlobal(String name, TransactionType type) {
        CategoryView created = createCategory.create(
                new CreateCategoryCommand(name, type, DEFAULT_COLOR, null, null));
        return toImportCategory(created);
    }

    private static ImportCategory toImportCategory(CategoryView view) {
        Long accountId = view.account() == null ? null : view.account().id();
        return new ImportCategory(view.id(), view.name(), view.type(), accountId);
    }
}
