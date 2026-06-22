package com.xroig.finance.shared.web;

import com.xroig.finance.shared.domain.ConflictException;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DomainExceptionHandler} (stage H0c): each domain
 * exception subtype maps to its HTTP status with the message as {@code detail}.
 * The end-to-end {@code problem+json} wiring through the slice is exercised per
 * controller as each domain migrates (H1+).
 */
class DomainExceptionHandlerTest {

    private final DomainExceptionHandler handler = new DomainExceptionHandler();

    @Test
    void notFoundMapsTo404() {
        ProblemDetail pd = handler.handleNotFound(new NotFoundException("Cuenta no encontrada"));
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(pd.getDetail()).isEqualTo("Cuenta no encontrada");
    }

    @Test
    void conflictMapsTo409() {
        ProblemDetail pd = handler.handleConflict(new ConflictException("La cuenta tiene movimientos"));
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getDetail()).isEqualTo("La cuenta tiene movimientos");
    }

    @Test
    void validationMapsTo400() {
        ProblemDetail pd = handler.handleValidation(new ValidationException("Importe inválido"));
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getDetail()).isEqualTo("Importe inválido");
    }

    @Test
    void baseDomainFallbackMapsTo400() {
        // An anonymous subtype that is not one of the three specific ones.
        ProblemDetail pd = handler.handleDomain(new ValidationException("Regla de dominio violada") {
        });
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getDetail()).isEqualTo("Regla de dominio violada");
    }
}
