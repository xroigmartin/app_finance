package com.xroig.finance.shared.infrastructure.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.logging.structured.StructuredLogFormatter;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Renders a Logback event as one JSON line shaped like the OpenTelemetry Logs Data
 * Model (Timestamp/SeverityText/SeverityNumber/Body/TraceId/SpanId/Attributes/
 * Resource): {@code TraceId}/{@code SpanId} come from the MDC (populated per HTTP
 * request once Micrometer Tracing is on the classpath, absent otherwise) and
 * {@code Attributes} from SLF4J's fluent key-value pairs plus the logger name.
 * Registered as a custom {@code logging.structured.format} both for
 * {@code system.log} (root logger) and {@code business.log} ({@code business.*}
 * loggers) — same shape, different file, so an OTel Collector can later tail either
 * without any code change here.
 */
public class OtelJsonLogFormatter implements StructuredLogFormatter<ILoggingEvent> {

    private static final String SERVICE_NAME = "finance-backend";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String format(ILoggingEvent event) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("Timestamp", OffsetDateTime.ofInstant(event.getInstant(), ZoneId.systemDefault()).toString());
        root.put("SeverityText", event.getLevel().toString());
        root.put("SeverityNumber", severityNumber(event.getLevel()));
        root.put("Body", event.getFormattedMessage());

        Map<String, String> mdc = event.getMDCPropertyMap();
        String traceId = mdc == null ? null : mdc.get("traceId");
        String spanId = mdc == null ? null : mdc.get("spanId");
        if (traceId != null && !traceId.isBlank()) {
            root.put("TraceId", traceId);
        }
        if (spanId != null && !spanId.isBlank()) {
            root.put("SpanId", spanId);
        }

        ObjectNode attributes = root.putObject("Attributes");
        attributes.put("logger", event.getLoggerName());
        List<KeyValuePair> keyValuePairs = event.getKeyValuePairs();
        if (keyValuePairs != null) {
            for (KeyValuePair pair : keyValuePairs) {
                attributes.put(pair.key, String.valueOf(pair.value));
            }
        }

        root.putObject("Resource").put("service.name", SERVICE_NAME);

        return root.toString() + System.lineSeparator();
    }

    private static int severityNumber(Level level) {
        return switch (level.toInt()) {
            case Level.TRACE_INT -> 1;
            case Level.DEBUG_INT -> 5;
            case Level.INFO_INT -> 9;
            case Level.WARN_INT -> 13;
            case Level.ERROR_INT -> 17;
            default -> 0;
        };
    }
}
