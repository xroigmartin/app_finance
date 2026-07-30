package com.xroig.finance.investments.infrastructure.web;

import com.xroig.finance.investments.application.FlexImportResult;
import com.xroig.finance.investments.application.ImportRecordQueryPort;
import com.xroig.finance.investments.application.ImportRecordView;
import com.xroig.finance.investments.application.IncomeView;
import com.xroig.finance.investments.application.InvestmentQueryPort;
import com.xroig.finance.investments.application.InvestmentsSummaryView;
import com.xroig.finance.investments.application.PerformanceView;
import com.xroig.finance.investments.application.PortfolioSummaryView;
import com.xroig.finance.investments.application.PositionView;
import com.xroig.finance.investments.application.ValuationHistoryView;
import com.xroig.finance.investments.application.port.CreatePortfolio;
import com.xroig.finance.investments.application.port.CreatePortfolio.CreatePortfolioCommand;
import com.xroig.finance.investments.application.port.DeletePortfolio;
import com.xroig.finance.investments.application.port.FindPortfolios;
import com.xroig.finance.investments.application.port.ImportFlexReport;
import com.xroig.finance.investments.application.port.UpdatePortfolio;
import com.xroig.finance.investments.application.port.UpdatePortfolio.UpdatePortfolioCommand;
import com.xroig.finance.shared.domain.Page;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Inbound web adapter for the portfolios of the investments context (§6). Thin by
 * design: it (de)serializes DTOs and delegates to the inbound ports — the CRUD to
 * the use cases, the read screens (positions, summary, valuation history, global
 * summary) to the CQRS {@link InvestmentQueryPort}, whose views serialize as-is.
 * Domain failures map to HTTP in {@code shared.web.DomainExceptionHandler}
 * (not found → 404, RN-5 guard → 409).
 */
@RestController
@RequestMapping("/api/investments")
@Tag(name = "Carteras (inversiones)", description = "Carteras de inversión: CRUD, importación de informes Flex de Interactive Brokers, posiciones, resumen, histórico de valoración, ingresos, rentabilidad e historial de importaciones.")
public class PortfolioController {

    private final FindPortfolios findPortfolios;
    private final CreatePortfolio createPortfolio;
    private final UpdatePortfolio updatePortfolio;
    private final DeletePortfolio deletePortfolio;
    private final ImportFlexReport importFlexReport;
    private final InvestmentQueryPort queries;
    private final ImportRecordQueryPort importHistory;

    public PortfolioController(FindPortfolios findPortfolios, CreatePortfolio createPortfolio,
                               UpdatePortfolio updatePortfolio, DeletePortfolio deletePortfolio,
                               ImportFlexReport importFlexReport, InvestmentQueryPort queries,
                               ImportRecordQueryPort importHistory) {
        this.findPortfolios = findPortfolios;
        this.createPortfolio = createPortfolio;
        this.updatePortfolio = updatePortfolio;
        this.deletePortfolio = deletePortfolio;
        this.importFlexReport = importFlexReport;
        this.queries = queries;
        this.importHistory = importHistory;
    }

    @Operation(summary = "Listar carteras")
    @ApiResponse(responseCode = "200", description = "Listado de carteras")
    @GetMapping("/portfolios")
    public List<PortfolioResponse> findAll() {
        return findPortfolios.all().stream().map(PortfolioResponse::from).toList();
    }

    @Operation(summary = "Crear cartera")
    @ApiResponse(responseCode = "201", description = "Cartera creada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (nombre vacío)", content = @Content)
    @PostMapping("/portfolios")
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioResponse create(@Valid @RequestBody PortfolioRequest request) {
        return PortfolioResponse.from(createPortfolio.create(
                new CreatePortfolioCommand(request.name(), request.baseCurrency())));
    }

    @Operation(summary = "Actualizar cartera", description = "Solo el nombre es editable; la divisa base no se puede cambiar.")
    @ApiResponse(responseCode = "200", description = "Cartera actualizada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (nombre vacío)", content = @Content)
    @ApiResponse(responseCode = "404", description = "Cartera no encontrada", content = @Content)
    @PutMapping("/portfolios/{id}")
    public PortfolioResponse update(@PathVariable Long id, @Valid @RequestBody PortfolioRequest request) {
        return PortfolioResponse.from(updatePortfolio.update(id, new UpdatePortfolioCommand(request.name())));
    }

    @Operation(summary = "Eliminar cartera", description = "Rechazada si tiene operaciones asociadas. Idempotente en lo demás: no falla si la cartera no existía.")
    @ApiResponse(responseCode = "204", description = "Cartera eliminada (o inexistente)")
    @ApiResponse(responseCode = "409", description = "La cartera tiene operaciones asociadas y no puede eliminarse", content = @Content)
    @DeleteMapping("/portfolios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deletePortfolio.delete(id);
    }

    @Operation(summary = "Importar informe Flex de Interactive Brokers",
            description = "La divisa base del informe debe coincidir con la de la cartera. Los duplicados (mismo external_id) no cuentan como error, se omiten; las filas ilegibles/no soportadas se reportan como error y el resto se importa; incluye avisos no bloqueantes (venta sin posición suficiente, tipo de cambio no encontrado).")
    @ApiResponse(responseCode = "200", description = "Resultado: nº importadas, nº duplicadas, errores y avisos por fila")
    @ApiResponse(responseCode = "400", description = "Divisa base del informe distinta de la de la cartera, o fichero ilegible", content = @Content)
    @ApiResponse(responseCode = "404", description = "Cartera no encontrada", content = @Content)
    @PostMapping("/portfolios/{id}/import")
    public FlexImportResult importFlex(@PathVariable Long id,
                                       @Parameter(description = "Informe Flex Query de Interactive Brokers (XML)") @RequestParam("file") MultipartFile file) {
        return importFlexReport.importReport(id, file);
    }

    @Operation(summary = "Posiciones actuales de una cartera")
    @ApiResponse(responseCode = "200", description = "Posiciones valoradas a hoy")
    @ApiResponse(responseCode = "404", description = "Cartera no encontrada", content = @Content)
    @GetMapping("/portfolios/{id}/positions")
    public List<PositionView> positions(@PathVariable Long id) {
        return queries.positions(id);
    }

    @Operation(summary = "Resumen de una cartera", description = "Valor total, aportaciones, plusvalía latente, liquidez por divisa y dividendos del año.")
    @ApiResponse(responseCode = "200", description = "Resumen de la cartera")
    @ApiResponse(responseCode = "404", description = "Cartera no encontrada", content = @Content)
    @GetMapping("/portfolios/{id}/summary")
    public PortfolioSummaryView summary(@PathVariable Long id) {
        return queries.summary(id);
    }

    @Operation(summary = "Histórico de valoración de una cartera")
    @ApiResponse(responseCode = "200", description = "Serie de valoraciones")
    @ApiResponse(responseCode = "404", description = "Cartera no encontrada", content = @Content)
    @GetMapping("/portfolios/{id}/valuation-history")
    public List<ValuationHistoryView> valuationHistory(@PathVariable Long id) {
        return queries.valuationHistory(id);
    }

    @Operation(summary = "Ingresos de una cartera", description = "Dividendos e intereses percibidos.")
    @ApiResponse(responseCode = "200", description = "Estado de ingresos")
    @ApiResponse(responseCode = "404", description = "Cartera no encontrada", content = @Content)
    @GetMapping("/portfolios/{id}/income")
    public IncomeView income(@PathVariable Long id) {
        return queries.income(id);
    }

    @Operation(summary = "Rentabilidad de una cartera")
    @ApiResponse(responseCode = "200", description = "Métricas de rentabilidad")
    @ApiResponse(responseCode = "404", description = "Cartera no encontrada", content = @Content)
    @GetMapping("/portfolios/{id}/performance")
    public PerformanceView performance(@PathVariable Long id) {
        return queries.performance(id);
    }

    @Operation(summary = "Resumen global de inversiones", description = "Agregado de todas las carteras (para el dashboard de inversiones).")
    @ApiResponse(responseCode = "200", description = "Resumen global")
    @GetMapping("/summary")
    public InvestmentsSummaryView globalSummary() {
        return queries.globalSummary();
    }

    @Operation(summary = "Historial de importaciones de una cartera", description = "Paginado; no falla si la cartera no existe, en ese caso devuelve una página vacía.")
    @ApiResponse(responseCode = "200", description = "Página de registros de importación (Flex)")
    @GetMapping("/portfolios/{id}/import-history")
    public Page<ImportRecordView> importHistory(@PathVariable Long id,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "25") int size) {
        return importHistory.history(id, page, size);
    }
}
