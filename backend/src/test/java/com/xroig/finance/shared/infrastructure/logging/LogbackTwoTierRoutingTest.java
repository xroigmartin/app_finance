package com.xroig.finance.shared.infrastructure.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check of {@code logback.xml}'s two-tier routing: a "business.*" logger
 * must land only in {@code business.log} (never {@code system.log}, additivity=false),
 * and everything else must land only in {@code system.log}. Loads the real production
 * config file into a throwaway {@link LoggerContext}, redirected to a temp dir via
 * {@code FINANCE_LOG_PATH} — no Spring context needed, since the config uses only
 * plain (non-Spring) property substitution.
 */
class LogbackTwoTierRoutingTest {

    private LoggerContext context;

    @AfterEach
    void stopContext() {
        if (context != null) {
            context.stop();
        }
        System.clearProperty("FINANCE_LOG_PATH");
    }

    private LoggerContext configuredContext(Path logPath) throws Exception {
        System.setProperty("FINANCE_LOG_PATH", logPath.toString());
        LoggerContext ctx = new LoggerContext();
        // A LoggerContext built by hand (outside SLF4J's normal static binding) has no
        // MDCAdapter of its own; without one, any getMDCPropertyMap() call NPEs.
        ctx.setMDCAdapter(new LogbackMDCAdapter());
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(ctx);
        URL config = getClass().getClassLoader().getResource("logback.xml");
        configurator.doConfigure(config);
        return ctx;
    }

    private List<JsonNode> readJsonLines(Path file) throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        ObjectMapper mapper = new ObjectMapper();
        return Files.readAllLines(file).stream()
                .filter(line -> !line.isBlank())
                .map(line -> {
                    try {
                        return mapper.readTree(line);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
    }

    @Test
    void businessLoggerLandsOnlyInBusinessLog(@TempDir Path tempDir) throws Exception {
        context = configuredContext(tempDir);
        context.getLogger("business.investments")
                .warn("import_row_rejected");
        context.stop();

        List<JsonNode> business = readJsonLines(tempDir.resolve("business.log"));
        List<JsonNode> system = readJsonLines(tempDir.resolve("system.log"));

        assertThat(business).hasSize(1);
        assertThat(business.getFirst().get("Body").asText()).isEqualTo("import_row_rejected");
        assertThat(system).isEmpty();
    }

    @Test
    void everythingElseLandsOnlyInSystemLog(@TempDir Path tempDir) throws Exception {
        context = configuredContext(tempDir);
        context.getLogger("com.xroig.finance.shared.web.DomainExceptionHandler")
                .error("fallo inesperado");
        context.stop();

        List<JsonNode> business = readJsonLines(tempDir.resolve("business.log"));
        List<JsonNode> system = readJsonLines(tempDir.resolve("system.log"));

        assertThat(system).hasSize(1);
        assertThat(system.getFirst().get("Body").asText()).isEqualTo("fallo inesperado");
        assertThat(business).isEmpty();
    }
}
