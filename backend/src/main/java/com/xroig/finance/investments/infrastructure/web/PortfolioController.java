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

    @GetMapping("/portfolios")
    public List<PortfolioResponse> findAll() {
        return findPortfolios.all().stream().map(PortfolioResponse::from).toList();
    }

    @PostMapping("/portfolios")
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioResponse create(@Valid @RequestBody PortfolioRequest request) {
        return PortfolioResponse.from(createPortfolio.create(
                new CreatePortfolioCommand(request.name(), request.baseCurrency())));
    }

    @PutMapping("/portfolios/{id}")
    public PortfolioResponse update(@PathVariable Long id, @Valid @RequestBody PortfolioRequest request) {
        return PortfolioResponse.from(updatePortfolio.update(id, new UpdatePortfolioCommand(request.name())));
    }

    @DeleteMapping("/portfolios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deletePortfolio.delete(id);
    }

    @PostMapping("/portfolios/{id}/import")
    public FlexImportResult importFlex(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return importFlexReport.importReport(id, file);
    }

    @GetMapping("/portfolios/{id}/positions")
    public List<PositionView> positions(@PathVariable Long id) {
        return queries.positions(id);
    }

    @GetMapping("/portfolios/{id}/summary")
    public PortfolioSummaryView summary(@PathVariable Long id) {
        return queries.summary(id);
    }

    @GetMapping("/portfolios/{id}/valuation-history")
    public List<ValuationHistoryView> valuationHistory(@PathVariable Long id) {
        return queries.valuationHistory(id);
    }

    @GetMapping("/portfolios/{id}/income")
    public IncomeView income(@PathVariable Long id) {
        return queries.income(id);
    }

    @GetMapping("/portfolios/{id}/performance")
    public PerformanceView performance(@PathVariable Long id) {
        return queries.performance(id);
    }

    @GetMapping("/summary")
    public InvestmentsSummaryView globalSummary() {
        return queries.globalSummary();
    }

    @GetMapping("/portfolios/{id}/import-history")
    public Page<ImportRecordView> importHistory(@PathVariable Long id,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "25") int size) {
        return importHistory.history(id, page, size);
    }
}
