package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.investments.application.FlexRowError;
import com.xroig.finance.investments.application.ImportRecordView;
import com.xroig.finance.investments.domain.ImportRecord;
import com.xroig.finance.investments.domain.ImportRowIssue;
import com.xroig.finance.investments.domain.Portfolio;
import com.xroig.finance.investments.domain.PortfolioId;
import com.xroig.finance.shared.domain.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Read-side adapter test (Level 2) against real PostgreSQL: {@link
 * ImportRecordQueryAdapter} paginates a portfolio's import history newest first
 * and deserializes errors/warnings back into {@link FlexRowError}/{@code String}.
 */
@Import({ImportRecordQueryAdapter.class, ImportRecordPersistenceAdapter.class, ImportRecordJpaMapper.class,
        PortfolioPersistenceAdapter.class, PortfolioJpaMapper.class, JacksonAutoConfiguration.class})
class ImportRecordQueryAdapterTest extends PostgresTestBase {

    @Autowired private ImportRecordQueryAdapter adapter;
    @Autowired private ImportRecordPersistenceAdapter writer;
    @Autowired private PortfolioPersistenceAdapter portfolios;

    private PortfolioId portfolioId;

    @BeforeEach
    void persistParentPortfolio() {
        portfolioId = portfolios.save(Portfolio.create("IBKR", "EUR")).id();
    }

    private void anImport(Instant importedAt, List<ImportRowIssue> errors, List<String> warnings) {
        writer.save(ImportRecord.of(portfolioId, importedAt, "flex.csv",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), 5, 1, errors, warnings));
    }

    @Test
    void history_paginatesNewestFirst() {
        anImport(Instant.parse("2026-01-01T10:00:00Z"), List.of(), List.of());
        anImport(Instant.parse("2026-03-01T10:00:00Z"),
                List.of(new ImportRowIssue("Trades", "T-1", "Instrumento desconocido")), List.of());
        anImport(Instant.parse("2026-05-01T10:00:00Z"), List.of(), List.of("2026-05-01: aviso"));

        Page<ImportRecordView> firstPage = adapter.history(portfolioId.value(), 0, 2);

        assertThat(firstPage.content()).extracting(ImportRecordView::importedAt)
                .containsExactly(Instant.parse("2026-05-01T10:00:00Z"), Instant.parse("2026-03-01T10:00:00Z"));
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);

        Page<ImportRecordView> secondPage = adapter.history(portfolioId.value(), 1, 2);
        assertThat(secondPage.content()).extracting(ImportRecordView::importedAt)
                .containsExactly(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void history_deserializesErrorsAndWarnings() {
        anImport(Instant.parse("2026-03-01T10:00:00Z"),
                List.of(new ImportRowIssue("Trades", "T-1", "Instrumento desconocido")),
                List.of("2026-03-01: venta sin posición suficiente"));

        ImportRecordView view = adapter.history(portfolioId.value(), 0, 10).content().getFirst();

        assertThat(view.errors()).containsExactly(new FlexRowError("Trades", "T-1", "Instrumento desconocido"));
        assertThat(view.warnings()).containsExactly("2026-03-01: venta sin posición suficiente");
        assertThat(view.imported()).isEqualTo(5);
        assertThat(view.duplicated()).isEqualTo(1);
        assertThat(view.fromDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(view.toDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(view.fileName()).isEqualTo("flex.csv");
    }

    @Test
    void history_onlyReturnsRowsOfItsOwnPortfolio() {
        PortfolioId other = portfolios.save(Portfolio.create("Otra", "EUR")).id();
        anImport(Instant.parse("2026-03-01T10:00:00Z"), List.of(), List.of());
        writer.save(ImportRecord.of(other, Instant.parse("2026-04-01T10:00:00Z"), "otra.csv",
                null, LocalDate.of(2026, 6, 30), 1, 0, List.of(), List.of()));

        Page<ImportRecordView> page = adapter.history(portfolioId.value(), 0, 10);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().getFirst().fileName()).isEqualTo("flex.csv");
    }
}
