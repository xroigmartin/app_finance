package com.xroig.finance.shared.web;

import com.xroig.finance.shared.domain.ConflictException;
import com.xroig.finance.shared.domain.DomainException;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
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
 */
@RestControllerAdvice
public class DomainExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleValidation(ValidationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Fallback for any other domain failure: a domain rule was violated → 400. */
    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
