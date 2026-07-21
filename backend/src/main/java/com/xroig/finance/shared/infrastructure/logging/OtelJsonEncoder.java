package com.xroig.finance.shared.infrastructure.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;

import java.nio.charset.StandardCharsets;

/**
 * Thin Logback {@code Encoder} wrapping {@link OtelJsonLogFormatter}. Deliberately
 * not Spring Boot's {@code StructuredLogEncoder}: that one requires a Spring
 * {@code Environment} stashed in the logger context by {@code LoggingSystem}
 * initialization, which only exists once the full application context is up — this
 * encoder has no such dependency, so {@code logback.xml} loads (and is testable)
 * with a bare {@code LoggerContext}, before Spring exists.
 */
public class OtelJsonEncoder extends EncoderBase<ILoggingEvent> {

    private final OtelJsonLogFormatter formatter = new OtelJsonLogFormatter();

    @Override
    public byte[] headerBytes() {
        return new byte[0];
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        return formatter.format(event).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] footerBytes() {
        return new byte[0];
    }
}
