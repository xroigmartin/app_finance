package com.xroig.finance.categories.infrastructure.web;

import com.xroig.finance.categories.application.CategoryView;
import com.xroig.finance.categories.application.port.CreateCategory;
import com.xroig.finance.categories.application.port.CreateCategory.CreateCategoryCommand;
import com.xroig.finance.categories.application.port.DeleteCategory;
import com.xroig.finance.categories.application.port.FindCategories;
import com.xroig.finance.categories.application.port.UpdateCategory;
import com.xroig.finance.categories.application.port.UpdateCategory.UpdateCategoryCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Inbound web adapter for the categories context. Thin: it (de)serializes DTOs and
 * delegates to the inbound ports, returning the {@link CategoryView} read model as
 * the JSON contract. Domain failures (404/409/400) are translated by {@code
 * shared.web.DomainExceptionHandler}.
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final FindCategories findCategories;
    private final CreateCategory createCategory;
    private final UpdateCategory updateCategory;
    private final DeleteCategory deleteCategory;

    public CategoryController(FindCategories findCategories, CreateCategory createCategory,
                             UpdateCategory updateCategory, DeleteCategory deleteCategory) {
        this.findCategories = findCategories;
        this.createCategory = createCategory;
        this.updateCategory = updateCategory;
        this.deleteCategory = deleteCategory;
    }

    @GetMapping
    public List<CategoryView> findAll() {
        return findCategories.all();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView create(@Valid @RequestBody CategoryRequest request) {
        return createCategory.create(new CreateCategoryCommand(
                request.name(), request.type(), request.color(), request.parentId(), request.accountId()));
    }

    @PutMapping("/{id}")
    public CategoryView update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return updateCategory.update(id, new UpdateCategoryCommand(
                request.name(), request.type(), request.color(), request.parentId(), request.accountId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deleteCategory.delete(id);
    }
}
