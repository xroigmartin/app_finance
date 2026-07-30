package com.xroig.finance.categories.infrastructure.web;

import com.xroig.finance.categories.application.CategoryView;
import com.xroig.finance.categories.application.port.CreateCategory;
import com.xroig.finance.categories.application.port.CreateCategory.CreateCategoryCommand;
import com.xroig.finance.categories.application.port.DeleteCategory;
import com.xroig.finance.categories.application.port.FindCategories;
import com.xroig.finance.categories.application.port.UpdateCategory;
import com.xroig.finance.categories.application.port.UpdateCategory.UpdateCategoryCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Categorías", description = "Categorías (globales o ligadas a cuenta) con un nivel de subcategorías. Puede llevar una recurrencia (ver /recurrence).")
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

    @Operation(summary = "Listar categorías", description = "Devuelve el árbol completo (categorías de primer nivel con sus subcategorías).")
    @ApiResponse(responseCode = "200", description = "Listado de categorías")
    @GetMapping
    public List<CategoryView> findAll() {
        return findCategories.all();
    }

    @Operation(summary = "Crear categoría", description = "Una subcategoría hereda el tipo de su padre; el ámbito de cuenta solo se hereda si el padre está ligado a una cuenta.")
    @ApiResponse(responseCode = "201", description = "Categoría creada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, categoría padre o cuenta no válida, o regla de jerarquía violada (más de un nivel, padre que no es de primer nivel...)", content = @Content)
    @ApiResponse(responseCode = "409", description = "El padre tiene una recurrencia y no admite subcategorías hasta quitarla, o ya existe una categoría con ese nombre en el mismo ámbito", content = @Content)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView create(@Valid @RequestBody CategoryRequest request) {
        return createCategory.create(new CreateCategoryCommand(
                request.name(), request.type(), request.color(), request.parentId(), request.accountId()));
    }

    @Operation(summary = "Actualizar categoría", description = "Reasignar a una cuenta concreta solo se permite si todas sus subcategorías ya pertenecen a esa misma cuenta.")
    @ApiResponse(responseCode = "200", description = "Categoría actualizada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, categoría padre/cuenta no válida o regla de jerarquía violada", content = @Content)
    @ApiResponse(responseCode = "404", description = "Categoría no encontrada", content = @Content)
    @ApiResponse(responseCode = "409", description = "Movimientos de otra cuenta que impiden el cambio de ámbito, recurrencia que impide hacerla global, o nombre duplicado en el mismo ámbito", content = @Content)
    @PutMapping("/{id}")
    public CategoryView update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return updateCategory.update(id, new UpdateCategoryCommand(
                request.name(), request.type(), request.color(), request.parentId(), request.accountId()));
    }

    @Operation(summary = "Eliminar categoría",
            description = "Rechazada si tiene subcategorías, movimientos, un presupuesto o reglas de categorización asociadas. Idempotente en lo demás: no falla si la categoría no existía.")
    @ApiResponse(responseCode = "204", description = "Categoría eliminada (o inexistente)")
    @ApiResponse(responseCode = "409", description = "La categoría tiene subcategorías, movimientos, presupuesto o reglas asociadas y no puede eliminarse", content = @Content)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deleteCategory.delete(id);
    }
}
