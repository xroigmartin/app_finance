package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.investments.domain.ImportRecord;
import com.xroig.finance.investments.domain.ImportRowIssue;
import com.xroig.finance.investments.domain.Portfolio;
import com.xroig.finance.investments.domain.PortfolioId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adapter test (Level 2) against real PostgreSQL: the
 * {@link ImportRecordPersistenceAdapter} + {@link ImportRecordJpaMapper}
 * round-trip preserves errors/warnings as JSON (order and content), and the
 * {@code to_date NOT NULL} constraint of the V8 migration is enforced.
 */
@Import({ImportRecordPersistenceAdapter.class, ImportRecordJpaMapper.class,
        PortfolioPersistenceAdapter.class, PortfolioJpaMapper.class, JacksonAutoConfiguration.class})
class ImportRecordPersistenceAdapterTest extends PostgresTestBase {

    @Autowired private ImportRecordPersistenceAdapter adapter;
    @Autowired private ImportRecordJpaRepository jpa;
    @Autowired private PortfolioPersistenceAdapter portfolios;
    @Autowired private ObjectMapper objectMapper;

    private PortfolioId portfolioId;

    @BeforeEach
    void persistParentPortfolio() {
        portfolioId = portfolios.save(Portfolio.create("IBKR", "EUR")).id();
    }

    @Test
    void save_roundTripsErrorsAndWarningsAsJson() {
        List<ImportRowIssue> errors = List.of(
                new ImportRowIssue("Trades", "T-1", "Instrumento desconocido"),
                new ImportRowIssue("CashTransactions", "CT-9", "Divisa no soportada"));
        List<String> warnings = List.of("2026-03-01: venta sin posición suficiente");

        ImportRecord saved = adapter.save(ImportRecord.of(portfolioId, Instant.parse("2026-07-26T10:15:30Z"),
                "flex-2026-h1.csv", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30),
                12, 3, errors, warnings));

        assertThat(saved.id()).isNotNull();
        ImportRecordJpaEntity row = jpa.findById(saved.id()).orElseThrow();
        assertThat(row.getPortfolioId()).isEqualTo(portfolioId.value());
        assertThat(row.getImportedAt()).isEqualTo(Instant.parse("2026-07-26T10:15:30Z"));
        assertThat(row.getFileName()).isEqualTo("flex-2026-h1.csv");
        assertThat(row.getFromDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(row.getToDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(row.getImportedCount()).isEqualTo(12);
        assertThat(row.getDuplicatedCount()).isEqualTo(3);
        assertThat(objectMapper.readValue(row.getErrors(), ImportRowIssue[].class))
                .containsExactlyElementsOf(errors);
        assertThat(objectMapper.readValue(row.getWarnings(), String[].class))
                .containsExactlyElementsOf(warnings);

        assertThat(saved.errors()).containsExactlyElementsOf(errors);
        assertThat(saved.warnings()).containsExactlyElementsOf(warnings);
    }

    @Test
    void save_withoutToDate_violatesTheNotNullConstraint() {
        ImportRecordJpaEntity entity = new ImportRecordJpaEntity();
        entity.setPortfolioId(portfolioId.value());
        entity.setImportedAt(Instant.now());
        entity.setImportedCount(0);
        entity.setDuplicatedCount(0);
        entity.setErrors("[]");
        entity.setWarnings("[]");

        assertThatThrownBy(() -> {
            jpa.save(entity);
            jpa.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_withEmptyErrorsAndWarnings_roundTrips() {
        ImportRecord saved = adapter.save(ImportRecord.of(portfolioId, Instant.parse("2026-07-26T10:15:30Z"),
                null, null, LocalDate.of(2026, 6, 30), 0, 0, List.of(), List.of()));

        ImportRecordJpaEntity row = jpa.findById(saved.id()).orElseThrow();
        assertThat(row.getFileName()).isNull();
        assertThat(row.getFromDate()).isNull();
        assertThat(saved.errors()).isEmpty();
        assertThat(saved.warnings()).isEmpty();
    }
}
