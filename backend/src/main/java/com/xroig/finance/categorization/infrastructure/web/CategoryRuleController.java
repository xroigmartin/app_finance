package com.xroig.finance.categorization.infrastructure.web;

import com.xroig.finance.categorization.application.CategoryRuleView;
import com.xroig.finance.categorization.application.RuleSaved;
import com.xroig.finance.categorization.application.port.CreateRule;
import com.xroig.finance.categorization.application.port.CreateRule.RuleCommand;
import com.xroig.finance.categorization.application.port.DeleteRule;
import com.xroig.finance.categorization.application.port.FindRules;
import com.xroig.finance.categorization.application.port.UpdateRule;
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
 * Inbound web adapter for the categorization context. Thin: it (de)serializes DTOs and
 * delegates to the inbound ports, returning the {@link CategoryRuleView} read model on
 * read and the {@link RuleSaved} result (rule + recategorized count) on write.
 */
@RestController
@RequestMapping("/api/category-rules")
@Tag(name = "Reglas de categorización", description = "Reglas de patrón (alternativas separadas por '|', insensibles a mayúsculas/acentos) que auto-categorizan movimientos importados sin categoría, moviendo también los que ya cayeron en el fallback 'Otros gastos'/'Otros ingresos'.")
public class CategoryRuleController {

    private final FindRules findRules;
    private final CreateRule createRule;
    private final UpdateRule updateRule;
    private final DeleteRule deleteRule;

    public CategoryRuleController(FindRules findRules, CreateRule createRule,
                                 UpdateRule updateRule, DeleteRule deleteRule) {
        this.findRules = findRules;
        this.createRule = createRule;
        this.updateRule = updateRule;
        this.deleteRule = deleteRule;
    }

    @Operation(summary = "Listar reglas de categorización")
    @ApiResponse(responseCode = "200", description = "Listado de reglas")
    @GetMapping
    public List<CategoryRuleView> findAll() {
        return findRules.findAll();
    }

    @Operation(summary = "Crear regla", description = "Al crearla se aplica de inmediato a los movimientos ya existentes en el fallback que coincidan con el patrón.")
    @ApiResponse(responseCode = "201", description = "Regla creada, junto con el número de movimientos recategorizados")
    @ApiResponse(responseCode = "400", description = "Patrón vacío o categoría no válida", content = @Content)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleSaved create(@Valid @RequestBody CategoryRuleRequest request) {
        return createRule.create(toCommand(request));
    }

    @Operation(summary = "Actualizar regla", description = "Al guardar se vuelve a aplicar a los movimientos del fallback que coincidan con el nuevo patrón/categoría.")
    @ApiResponse(responseCode = "200", description = "Regla actualizada, junto con el número de movimientos recategorizados")
    @ApiResponse(responseCode = "400", description = "Patrón vacío o categoría no válida", content = @Content)
    @ApiResponse(responseCode = "404", description = "Regla no encontrada", content = @Content)
    @PutMapping("/{id}")
    public RuleSaved update(@PathVariable Long id, @Valid @RequestBody CategoryRuleRequest request) {
        return updateRule.update(id, toCommand(request));
    }

    @Operation(summary = "Eliminar regla", description = "Idempotente: no falla si la regla no existía. No afecta a los movimientos ya categorizados.")
    @ApiResponse(responseCode = "204", description = "Regla eliminada (o inexistente)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deleteRule.delete(id);
    }

    private static RuleCommand toCommand(CategoryRuleRequest r) {
        return new RuleCommand(r.pattern(), r.categoryId());
    }
}
