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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@Tag(name = "Presupuestos", description = "Presupuestos mensuales por categoría hoja y cuenta, y la matriz anual (12 meses × categorías) planificado/real.")
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

    @Operation(summary = "Listar presupuestos de un mes", description = "year/month por defecto son el mes actual.")
    @ApiResponse(responseCode = "200", description = "Presupuestos del mes")
    @GetMapping
    public List<BudgetView> find(@RequestParam(required = false) Integer year,
                                 @RequestParam(required = false) Integer month,
                                 @RequestParam(required = false) Long accountId) {
        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();
        return findBudgets.find(y, m, accountId);
    }

    @Operation(summary = "Matriz anual de presupuestos",
            description = "12 meses × categorías, planificado/real/diferencia, más TOTAL INGRESOS, TOTAL GASTOS, AHORRO, % y AHORRO ACUMULADO. Las categorías con subcategorías aparecen como fila agregada de solo lectura.")
    @ApiResponse(responseCode = "200", description = "Matriz anual")
    @GetMapping("/annual")
    public AnnualBudgetView annual(@RequestParam(required = false) Integer year,
                                   @RequestParam(required = false) @Parameter(description = "Si se omite, las celdas no son editables (la edición inline requiere una cuenta concreta).") Long accountId) {
        int y = year != null ? year : LocalDate.now().getYear();
        return findBudgets.annual(y, accountId);
    }

    @Operation(summary = "Crear presupuesto", description = "Solo sobre categorías hoja (sin subcategorías); una por cuenta+categoría+mes.")
    @ApiResponse(responseCode = "201", description = "Presupuesto creado")
    @ApiResponse(responseCode = "400", description = "Cuenta/categoría no válida o categoría con subcategorías", content = @Content)
    @ApiResponse(responseCode = "409", description = "Ya existe un presupuesto para esa cuenta/categoría/mes", content = @Content)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetView create(@Valid @RequestBody BudgetRequest request) {
        return createBudget.create(toCommand(request));
    }

    @Operation(summary = "Actualizar presupuesto")
    @ApiResponse(responseCode = "200", description = "Presupuesto actualizado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content)
    @ApiResponse(responseCode = "404", description = "Presupuesto no encontrado", content = @Content)
    @PutMapping("/{id}")
    public BudgetView update(@PathVariable Long id, @Valid @RequestBody BudgetRequest request) {
        return updateBudget.update(id, toCommand(request));
    }

    @Operation(summary = "Eliminar presupuesto", description = "Idempotente: no falla si el presupuesto no existía.")
    @ApiResponse(responseCode = "204", description = "Presupuesto eliminado (o inexistente)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deleteBudget.delete(id);
    }

    @Operation(summary = "Copiar presupuestos de un mes a otro",
            description = "Copia los presupuestos de fromYear/fromMonth a toYear/toMonth, omitiendo los que ya existan en destino.")
    @ApiResponse(responseCode = "200", description = "Presupuestos recién copiados (no incluye los que ya existían en destino)")
    @PostMapping("/copy")
    public List<BudgetView> copy(@Valid @RequestBody CopyRequest request) {
        return copyBudgets.copy(new CopyCommand(
                request.fromYear(), request.fromMonth(), request.toYear(), request.toMonth()));
    }

    private static BudgetCommand toCommand(BudgetRequest r) {
        return new BudgetCommand(r.accountId(), r.categoryId(), r.year(), r.month(), r.amount());
    }
}
