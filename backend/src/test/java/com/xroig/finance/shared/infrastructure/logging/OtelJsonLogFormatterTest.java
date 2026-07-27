package com.xroig.finance.shared.infrastructure.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link OtelJsonLogFormatter}: renders a Logback {@code ILoggingEvent}
 * as a single JSON line shaped like the OpenTelemetry Logs Data Model (Timestamp/
 * SeverityText/SeverityNumber/Body/TraceId/SpanId/Attributes/Resource), so business
 * and system logs are already OTLP-ready before an exporter ever exists.
 */
class OtelJsonLogFormatterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final LoggerContext context = new LoggerContext();
    private final OtelJsonLogFormatter formatter = new OtelJsonLogFormatter();

    private Logger logger(String name) {
        return context.getLogger(name);
    }

    /** By the time a real event reaches the encoder its MDC map is always set (possibly empty). */
    private LoggingEvent event(String loggerName, Level level, String message) {
        return event(loggerName, level, message, Map.of());
    }

    private LoggingEvent event(String loggerName, Level level, String message, Map<String, String> mdc) {
        LoggingEvent event = new LoggingEvent(Logger.class.getName(), logger(loggerName), level, message, null, null);
        event.setMDCPropertyMap(mdc);
        return event;
    }

    private JsonNode formatToJson(LoggingEvent event) throws Exception {
        return MAPPER.readTree(formatter.format(event));
    }

    @Test
    void formatsABusinessWarningWithAttributesAndTraceContext() throws Exception {
        LoggingEvent event = event("business.investments", Level.WARN, "import_row_rejected", Map.of(
                "traceId", "6c4f3f1a9e2b4d7c8a5f0e3d2c1b9a8f",
                "spanId", "3a2b1c0d9e8f7a6b"));
        event.setKeyValuePairs(List.of(
                new KeyValuePair("type", "TAX"),
                new KeyValuePair("external_id", "CT-3702536094"),
                new KeyValuePair("amount", "0.46")));

        JsonNode json = formatToJson(event);

        assertThat(json.get("SeverityText").asText()).isEqualTo("WARN");
        assertThat(json.get("SeverityNumber").asInt()).isEqualTo(13);
        assertThat(json.get("Body").asText()).isEqualTo("import_row_rejected");
        assertThat(json.get("TraceId").asText()).isEqualTo("6c4f3f1a9e2b4d7c8a5f0e3d2c1b9a8f");
        assertThat(json.get("SpanId").asText()).isEqualTo("3a2b1c0d9e8f7a6b");
        assertThat(json.get("Attributes").get("logger").asText()).isEqualTo("business.investments");
        assertThat(json.get("Attributes").get("type").asText()).isEqualTo("TAX");
        assertThat(json.get("Attributes").get("external_id").asText()).isEqualTo("CT-3702536094");
        assertThat(json.get("Attributes").get("amount").asText()).isEqualTo("0.46");
        assertThat(json.get("Resource").get("service.name").asText()).isEqualTo("finance-backend");
    }

    @Test
    void timestampIsAParsableIsoOffsetInstant() throws Exception {
        JsonNode json = formatToJson(event("system", Level.ERROR, "boom"));

        assertThatCode(() -> OffsetDateTime.parse(json.get("Timestamp").asText())).doesNotThrowAnyException();
    }

    @Test
    void omitsTraceAndSpanWhenThereIsNoTraceContext() throws Exception {
        JsonNode json = formatToJson(event("system", Level.ERROR, "sin contexto de traza"));

        assertThat(json.has("TraceId")).isFalse();
        assertThat(json.has("SpanId")).isFalse();
    }

    @Test
    void attributesHoldOnlyTheLoggerNameWhenThereAreNoKeyValuePairs() throws Exception {
        JsonNode json = formatToJson(event("system.startup", Level.INFO, "arrancando"));

        assertThat(json.get("Attributes").properties()).hasSize(1);
        assertThat(json.get("Attributes").get("logger").asText()).isEqualTo("system.startup");
    }

    @Test
    void mapsEverySeverityLevelToItsOtelSeverityNumber() throws Exception {
        assertThat(formatToJson(eventAt(Level.TRACE)).get("SeverityNumber").asInt()).isEqualTo(1);
        assertThat(formatToJson(eventAt(Level.DEBUG)).get("SeverityNumber").asInt()).isEqualTo(5);
        assertThat(formatToJson(eventAt(Level.INFO)).get("SeverityNumber").asInt()).isEqualTo(9);
        assertThat(formatToJson(eventAt(Level.WARN)).get("SeverityNumber").asInt()).isEqualTo(13);
        assertThat(formatToJson(eventAt(Level.ERROR)).get("SeverityNumber").asInt()).isEqualTo(17);
    }

    private LoggingEvent eventAt(Level level) {
        return event("system", level, "msg");
    }
}
