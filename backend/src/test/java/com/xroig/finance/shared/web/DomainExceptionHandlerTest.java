package com.xroig.finance.shared.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.xroig.finance.shared.domain.ConflictException;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DomainExceptionHandler} (stage H0c): each domain
 * exception subtype maps to its HTTP status with the message as {@code detail},
 * and leaves a WARN trace in the system log (§4 RF-4 of
 * {@code docs/prd/observabilidad.md}) so a 4xx can be diagnosed from
 * {@code system.log} without reproducing the request. The end-to-end
 * {@code problem+json} wiring through the slice is exercised per controller as
 * each domain migrates (H1+).
 */
class DomainExceptionHandlerTest {

    private final DomainExceptionHandler handler = new DomainExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(DomainExceptionHandler.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(logs);
    }

    private String attribute(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals(key))
                .map(pair -> String.valueOf(pair.value))
                .findFirst()
                .orElse(null);
    }

    @Test
    void notFoundMapsTo404() {
        ProblemDetail pd = handler.handleNotFound(new NotFoundException("Cuenta no encontrada"));
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(pd.getDetail()).isEqualTo("Cuenta no encontrada");

        assertThat(logs.list).hasSize(1);
        ILoggingEvent event = logs.list.getFirst();
        assertThat(event.getLevel().toString()).isEqualTo("WARN");
        assertThat(attribute(event, "exception")).isEqualTo("NotFoundException");
        assertThat(attribute(event, "detail")).isEqualTo("Cuenta no encontrada");
    }

    @Test
    void conflictMapsTo409() {
        ProblemDetail pd = handler.handleConflict(new ConflictException("La cuenta tiene movimientos"));
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getDetail()).isEqualTo("La cuenta tiene movimientos");

        ILoggingEvent event = logs.list.getFirst();
        assertThat(attribute(event, "exception")).isEqualTo("ConflictException");
    }

    @Test
    void validationMapsTo400() {
        ProblemDetail pd = handler.handleValidation(new ValidationException("Importe inválido"));
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getDetail()).isEqualTo("Importe inválido");

        ILoggingEvent event = logs.list.getFirst();
        assertThat(attribute(event, "exception")).isEqualTo("ValidationException");
    }

    @Test
    void baseDomainFallbackMapsTo400() {
        // An anonymous subtype that is not one of the three specific ones.
        ProblemDetail pd = handler.handleDomain(new ValidationException("Regla de dominio violada") {
        });
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getDetail()).isEqualTo("Regla de dominio violada");

        assertThat(logs.list).hasSize(1);
    }
}
