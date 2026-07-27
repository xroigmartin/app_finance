package com.xroig.finance.investments.infrastructure.flex;

import com.xroig.finance.investments.application.FlexReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Smoke net over the real Flex reports kept in {@code docs/investment/} (the
 * reference material of the plan): the parser must consume both full years
 * without a single row error — §9's "validated Flex configuration" stays true
 * against the actual data, not just the trimmed fixture. Skipped silently when
 * the reports are not present (they are user data, not test resources).
 */
class RealFlexSmokeTest {

    @ParameterizedTest
    @ValueSource(strings = {"2024", "2025"})
    void realReport_parsesWithoutRowErrors(String year) throws Exception {
        Path file = Path.of("../docs/investment/" + year + ".xml");
        assumeTrue(Files.exists(file), "informe real no disponible");

        FlexReport report = new FlexReportParser().read(new MockMultipartFile(
                "file", year + ".xml", "text/xml", Files.readAllBytes(file)));

        assertThat(report.errors()).isEmpty();
        assertThat(report.baseCurrency()).isEqualTo("EUR");
        assertThat(report.rows()).isNotEmpty();
        assertThat(report.quotes()).isNotEmpty();
        assertThat(report.exchangeRates()).isNotEmpty();
    }
}
