package com.xroig.finance.investments.infrastructure.web;

import com.xroig.finance.investments.application.FlexImportResult;
import com.xroig.finance.investments.application.FlexRowError;
import com.xroig.finance.investments.application.IncomeView;
import com.xroig.finance.investments.application.InvestmentQueryPort;
import com.xroig.finance.investments.application.InvestmentsSummaryView;
import com.xroig.finance.investments.application.InvestmentsSummaryView.PortfolioValueView;
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
import com.xroig.finance.investments.domain.Portfolio;
import com.xroig.finance.investments.domain.PortfolioId;
import com.xroig.finance.shared.domain.ConflictException;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HTTP contract of the {@link PortfolioController} (§6): routing under
 * {@code /api/investments}, JSON shapes, {@code @Valid} → 400, and the shared
 * domain advice (not found → 404, RN-5 deletion guard → 409 {@code problem+json}).
 * The slice mocks the inbound ports and the read-side query port.
 */
@WebMvcTest(PortfolioController.class)
class PortfolioControllerMvcTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private FindPortfolios findPortfolios;
    @MockitoBean private CreatePortfolio createPortfolio;
    @MockitoBean private UpdatePortfolio updatePortfolio;
    @MockitoBean private DeletePortfolio deletePortfolio;
    @MockitoBean private InvestmentQueryPort queries;
    @MockitoBean private ImportFlexReport importFlexReport;

    private static Portfolio portfolio(long id, String name) {
        return Portfolio.rehydrate(new PortfolioId(id), name, "EUR");
    }

    @Test
    void findAll_returns200WithJsonArray() {
        when(findPortfolios.all()).thenReturn(List.of(portfolio(1L, "IBKR")));

        assertThat(mvc.get().uri("/api/investments/portfolios"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson().extractingPath("$[0].name").isEqualTo("IBKR");
    }

    @Test
    void income_returns200WithTheIncomeView() {
        when(queries.income(7L)).thenReturn(new IncomeView(7L, "EUR",
                List.of(new IncomeView.IncomeEntryView(42L, "Vanguard FTSE All-World", "2025-03",
                        new BigDecimal("100"), new BigDecimal("15"), new BigDecimal("85"))),
                List.of(new IncomeView.MonthAmountView("2025-03", new BigDecimal("5"))),
                List.of(new IncomeView.MonthAmountView("2025-03", new BigDecimal("15")))));

        var result = mvc.get().uri("/api/investments/portfolios/7/income").exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.baseCurrency").isEqualTo("EUR");
        assertThat(result).bodyJson().extractingPath("$.incomes[0].month").isEqualTo("2025-03");
        assertThat(result).bodyJson().extractingPath("$.incomes[0].net").asNumber().isEqualTo(85);
        assertThat(result).bodyJson().extractingPath("$.fees[0].amount").asNumber().isEqualTo(5);
        assertThat(result).bodyJson().extractingPath("$.taxes[0].amount").asNumber().isEqualTo(15);
    }

    @Test
    void income_unknownPortfolio_returns404() {
        when(queries.income(99L)).thenThrow(new NotFoundException("Cartera no encontrada"));

        assertThat(mvc.get().uri("/api/investments/portfolios/99/income"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void importFlex_returns200WithTheSummary() {
        when(importFlexReport.importReport(eq(7L), any()))
                .thenReturn(new FlexImportResult(120, 4,
                        List.of(new FlexRowError("CorporateActions", "CA-9", "tipo no soportado: TC")),
                        List.of("2025-05-20: venta sin posición suficiente: falta histórico anterior (RN-4)")));

        var result = mvc.post().uri("/api/investments/portfolios/7/import")
                .multipart()
                .file(new MockMultipartFile(
                        "file", "flex.xml", MediaType.APPLICATION_XML_VALUE, "<FlexQueryResponse/>".getBytes()))
                .exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.imported").asNumber().isEqualTo(120);
        assertThat(result).bodyJson().extractingPath("$.duplicated").asNumber().isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.errors[0].reference").isEqualTo("CA-9");
        assertThat(result).bodyJson().extractingPath("$.warnings[0]").asString().contains("RN-4");
    }

    @Test
    void importFlex_baseCurrencyMismatch_returns400ProblemJson() {
        when(importFlexReport.importReport(anyLong(), any()))
                .thenThrow(new ValidationException("La divisa base de la cuenta del informe (USD) no coincide"));

        assertThat(mvc.post().uri("/api/investments/portfolios/7/import")
                .multipart()
                .file(new MockMultipartFile(
                        "file", "flex.xml", MediaType.APPLICATION_XML_VALUE, "<FlexQueryResponse/>".getBytes())))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void importFlex_unknownPortfolio_returns404() {
        when(importFlexReport.importReport(anyLong(), any()))
                .thenThrow(new NotFoundException("Cartera no encontrada"));

        assertThat(mvc.post().uri("/api/investments/portfolios/99/import")
                .multipart()
                .file(new MockMultipartFile(
                        "file", "flex.xml", MediaType.APPLICATION_XML_VALUE, "<FlexQueryResponse/>".getBytes())))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void create_valid_returns201WithBaseCurrency() {
        when(createPortfolio.create(any(CreatePortfolioCommand.class)))
                .thenReturn(portfolio(7L, "IBKR"));

        assertThat(mvc.post().uri("/api/investments/portfolios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"IBKR","baseCurrency":"EUR"}
                        """))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.baseCurrency").isEqualTo("EUR");
    }

    @Test
    void create_blankName_returns400AndDoesNotCallUseCase() {
        assertThat(mvc.post().uri("/api/investments/portfolios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"","baseCurrency":"EUR"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verify(createPortfolio, never()).create(any());
    }

    @Test
    void update_renames_returns200() {
        when(updatePortfolio.update(anyLong(), any(UpdatePortfolioCommand.class)))
                .thenReturn(portfolio(7L, "Nuevo nombre"));

        assertThat(mvc.put().uri("/api/investments/portfolios/7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Nuevo nombre"}
                        """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.name").isEqualTo("Nuevo nombre");
    }

    @Test
    void update_missingPortfolio_returns404ProblemJson() {
        when(updatePortfolio.update(anyLong(), any(UpdatePortfolioCommand.class)))
                .thenThrow(new NotFoundException("Cartera no encontrada"));

        assertThat(mvc.put().uri("/api/investments/portfolios/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Da igual"}
                        """))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void delete_returns204() {
        assertThat(mvc.delete().uri("/api/investments/portfolios/7"))
                .hasStatus(HttpStatus.NO_CONTENT);
        verify(deletePortfolio).delete(7L);
    }

    @Test
    void delete_withOperations_returns409ProblemJson() {
        doThrow(new ConflictException("La cartera tiene operaciones asociadas y no se puede eliminar"))
                .when(deletePortfolio).delete(7L);

        assertThat(mvc.delete().uri("/api/investments/portfolios/7"))
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void positions_returns200WithTheViewShape() {
        when(queries.positions(7L)).thenReturn(List.of(new PositionView(
                3L, "IE00BK5BQT80", "Vanguard FTSE All-World", "VWCE", "EUR",
                new BigDecimal("10"), new BigDecimal("100.2000"), new BigDecimal("1002.0000"),
                new BigDecimal("110.00000000"), LocalDate.of(2026, 7, 9),
                new BigDecimal("1100.0000"), new BigDecimal("98.0000"),
                new BigDecimal("9.78"), new BigDecimal("60.00"), false)));

        assertThat(mvc.get().uri("/api/investments/portfolios/7/positions"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[0].isin").isEqualTo("IE00BK5BQT80");
    }

    @Test
    void positions_ofMissingPortfolio_returns404() {
        when(queries.positions(99L)).thenThrow(new NotFoundException("Cartera no encontrada"));

        assertThat(mvc.get().uri("/api/investments/portfolios/99/positions"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void summary_returns200WithTheKpis() {
        when(queries.summary(7L)).thenReturn(new PortfolioSummaryView(
                7L, "IBKR", "EUR", new BigDecimal("1638.0000"), LocalDate.of(2026, 7, 9),
                new BigDecimal("1500.0000"), new BigDecimal("98.0000"), new BigDecimal("9.78"),
                Map.of("EUR", new BigDecimal("538.0000")), new BigDecimal("40.0000")));

        assertThat(mvc.get().uri("/api/investments/portfolios/7/summary"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.cashByCurrency.EUR").isEqualTo(538.0);
    }

    @Test
    void valuationHistory_returns200WithTheSeries() {
        when(queries.valuationHistory(7L)).thenReturn(List.of(
                new ValuationHistoryView(LocalDate.of(2026, 6, 1),
                        new BigDecimal("1000.0000"), new BigDecimal("1000.0000"))));

        assertThat(mvc.get().uri("/api/investments/portfolios/7/valuation-history"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[0].date").isEqualTo("2026-06-01");
    }

    @Test
    void globalSummary_returns200WithThePerPortfolioBreakdown() {
        when(queries.globalSummary()).thenReturn(new InvestmentsSummaryView(
                new BigDecimal("1640.0000"), LocalDate.of(2026, 7, 7),
                List.of(new PortfolioValueView(7L, "IBKR", "EUR",
                        new BigDecimal("1100.0000"), LocalDate.of(2026, 7, 7)))));

        assertThat(mvc.get().uri("/api/investments/summary"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.portfolios[0].name").isEqualTo("IBKR");
    }
}
