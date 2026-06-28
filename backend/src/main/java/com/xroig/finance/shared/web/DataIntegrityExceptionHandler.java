package com.xroig.finance.shared.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;

/**
 * Web adapter that turns low-level persistence failures into clear HTTP responses
 * ({@code application/problem+json}). Without this, a unique-constraint violation
 * bubbles up as a bare {@code 500} with no detail, which the UI cannot tell apart
 * from any other server error.
 *
 * <p>It is a last-resort safety net: most conflicts are pre-checked by the
 * application services and surface as {@link com.xroig.finance.shared.domain.ConflictException}
 * via {@link DomainExceptionHandler}. The cases that are only guarded by a database
 * constraint (e.g. category name uniqueness per scope) land here instead.
 */
@RestControllerAdvice
public class DataIntegrityExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, messageFor(ex));
    }

    /** Maps the offending constraint to a user-facing Spanish message. */
    private String messageFor(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        String marker = cause == null ? "" : cause.toLowerCase(Locale.ROOT);
        if (marker.contains("ux_categories_name_scope")) {
            return "Ya existe una categoría con ese nombre en ese ámbito.";
        }
        if (marker.contains("uq_amount_vigencia")) {
            return "Ya hay un importe con esa fecha de vigencia en la recurrencia.";
        }
        if (marker.contains("recurring_budgets_category_id")) {
            return "La categoría ya tiene una recurrencia definida.";
        }
        if (marker.contains("uq_monthly_budgets_account_category_period")) {
            return "Ya existe un presupuesto para esa categoría, cuenta y periodo.";
        }
        return "La operación viola una restricción de integridad de datos.";
    }
}
