package com.xroig.finance.shared.web;

import com.xroig.finance.shared.domain.ConflictException;
import com.xroig.finance.shared.domain.DomainException;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Web adapter that translates the domain's own failures into HTTP responses
 * ({@code application/problem+json}), keeping {@code HttpStatus} out of the
 * domain. Each subtype of {@link DomainException} carries a user-facing Spanish
 * message that becomes the {@code detail}.
 *
 * <p>Sibling of {@link DataIntegrityExceptionHandler}, which maps the last-resort
 * {@code DataIntegrityViolationException} from the database; the two handle
 * disjoint exception types.
 *
 * <p>Every mapped exception also leaves a WARN trace in {@code system.log} (RF-4
 * of {@code docs/prd/observabilidad.md}) so a 4xx can be diagnosed from the log
 * alone, without reproducing the request.
 */
@RestControllerAdvice
public class DomainExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DomainExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        logDomainException(ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        logDomainException(ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleValidation(ValidationException ex) {
        logDomainException(ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Fallback for any other domain failure: a domain rule was violated → 400. */
    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex) {
        logDomainException(ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private void logDomainException(DomainException ex) {
        log.atWarn()
                .setMessage("domain_exception")
                .addKeyValue("exception", ex.getClass().getSimpleName())
                .addKeyValue("detail", ex.getMessage())
                .log();
    }
}
