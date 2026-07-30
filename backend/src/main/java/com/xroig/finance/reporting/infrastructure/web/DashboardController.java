package com.xroig.finance.reporting.infrastructure.web;

import com.xroig.finance.reporting.application.AccountComparisonView;
import com.xroig.finance.reporting.application.BalancePointView;
import com.xroig.finance.reporting.application.BudgetStatusView;
import com.xroig.finance.reporting.application.CategoryAmountView;
import com.xroig.finance.reporting.application.MonthlyPointView;
import com.xroig.finance.reporting.application.SummaryView;
import com.xroig.finance.reporting.application.port.DashboardReports;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Inbound web adapter for the reporting context (read-only). Thin: it resolves the default
 * year/month to "now", clamps {@code months} to [1, 36], builds the end month, and delegates
 * to the {@link DashboardReports} inbound port, returning the read models.
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Agregados de solo lectura para el panel principal: resumen, evolución mensual, desglose por categoría/cuenta y estado de presupuestos. year/month, si se omiten, son los del mes actual; accountId, si se omite, agrega todas las cuentas.")
public class DashboardController {

    private final DashboardReports reports;

    public DashboardController(DashboardReports reports) {
        this.reports = reports;
    }

    @Operation(summary = "Resumen del mes", description = "Ingresos, gastos y balance del mes.")
    @ApiResponse(responseCode = "200", description = "Resumen del mes")
    @GetMapping("/summary")
    public SummaryView summary(@RequestParam(required = false) Integer year,
                               @RequestParam(required = false) Integer month,
                               @RequestParam(required = false) Long accountId) {
        LocalDate now = LocalDate.now();
        return reports.summary(
                year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue(),
                accountId);
    }

    @Operation(summary = "Gastos por categoría del mes")
    @ApiResponse(responseCode = "200", description = "Gasto agregado por categoría (subcategorías agregadas en su padre)")
    @GetMapping("/expenses-by-category")
    public List<CategoryAmountView> expensesByCategory(@RequestParam(required = false) Integer year,
                                                       @RequestParam(required = false) Integer month,
                                                       @RequestParam(required = false) Long accountId) {
        LocalDate now = LocalDate.now();
        return reports.expensesByCategory(
                year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue(),
                accountId);
    }

    @Operation(summary = "Estado de presupuestos del mes", description = "Planificado vs. real por categoría presupuestada.")
    @ApiResponse(responseCode = "200", description = "Estado de cada presupuesto del mes")
    @GetMapping("/budgets")
    public List<BudgetStatusView> budgets(@RequestParam(required = false) Integer year,
                                          @RequestParam(required = false) Integer month,
                                          @RequestParam(required = false) Long accountId) {
        LocalDate now = LocalDate.now();
        return reports.budgetStatus(
                year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue(),
                accountId);
    }

    @Operation(summary = "Evolución mensual de ingresos/gastos", description = "months (clamp [1, 36]) meses hacia atrás desde year/month (por defecto, hoy).")
    @ApiResponse(responseCode = "200", description = "Un punto por mes")
    @GetMapping("/monthly")
    public List<MonthlyPointView> monthly(@RequestParam(defaultValue = "12") @Parameter(description = "Nº de meses hacia atrás; se ajusta al rango [1, 36]") int months,
                                          @RequestParam(required = false) Integer year,
                                          @RequestParam(required = false) Integer month,
                                          @RequestParam(required = false) Long accountId) {
        return reports.monthlyEvolution(clampMonths(months), endMonth(year, month), accountId);
    }

    @Operation(summary = "Ingresos por categoría del mes")
    @ApiResponse(responseCode = "200", description = "Ingreso agregado por categoría (subcategorías agregadas en su padre)")
    @GetMapping("/income-by-category")
    public List<CategoryAmountView> incomeByCategory(@RequestParam(required = false) Integer year,
                                                     @RequestParam(required = false) Integer month,
                                                     @RequestParam(required = false) Long accountId) {
        LocalDate now = LocalDate.now();
        return reports.incomeByCategory(
                year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue(),
                accountId);
    }

    @Operation(summary = "Evolución mensual del saldo", description = "months (clamp [1, 36]) meses hacia atrás desde year/month (por defecto, hoy).")
    @ApiResponse(responseCode = "200", description = "Un punto de saldo por mes")
    @GetMapping("/monthly-balance")
    public List<BalancePointView> monthlyBalance(@RequestParam(defaultValue = "12") @Parameter(description = "Nº de meses hacia atrás; se ajusta al rango [1, 36]") int months,
                                                 @RequestParam(required = false) Integer year,
                                                 @RequestParam(required = false) Integer month,
                                                 @RequestParam(required = false) Long accountId) {
        return reports.monthlyBalance(clampMonths(months), endMonth(year, month), accountId);
    }

    @Operation(summary = "Comparativa de saldo por cuenta", description = "months (clamp [1, 36]) meses hacia atrás desde year/month (por defecto, hoy). No admite filtro por cuenta (compara todas).")
    @ApiResponse(responseCode = "200", description = "Serie mensual de saldo por cuenta")
    @GetMapping("/by-account")
    public AccountComparisonView byAccount(@RequestParam(defaultValue = "12") @Parameter(description = "Nº de meses hacia atrás; se ajusta al rango [1, 36]") int months,
                                           @RequestParam(required = false) Integer year,
                                           @RequestParam(required = false) Integer month) {
        return reports.accountComparison(clampMonths(months), endMonth(year, month));
    }

    private static int clampMonths(int months) {
        return Math.min(Math.max(months, 1), 36);
    }

    private static YearMonth endMonth(Integer year, Integer month) {
        LocalDate now = LocalDate.now();
        return YearMonth.of(year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue());
    }
}
