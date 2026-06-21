package com.xroig.finance.controller;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GlobalExceptionHandler} (stage E3e): each known unique
 * constraint maps to its Spanish message with a 409, and anything else (or a
 * null cause message) falls back to the generic integrity message.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /** Builds the exception with a most-specific cause carrying {@code causeMessage}. */
    private ProblemDetail handle(String causeMessage) {
        return handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("wrapper", new RuntimeException(causeMessage)));
    }

    @Test
    void categoryNameScopeConstraint() {
        ProblemDetail pd = handle(
                "ERROR: duplicate key value violates unique constraint \"UX_CATEGORIES_NAME_SCOPE\"");
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getDetail()).isEqualTo("Ya existe una categoría con ese nombre en ese ámbito.");
    }

    @Test
    void recurrenceAmountValidityConstraint() {
        ProblemDetail pd = handle("violates unique constraint \"uq_amount_vigencia\"");
        assertThat(pd.getDetail()).isEqualTo("Ya hay un importe con esa fecha de vigencia en la recurrencia.");
    }

    @Test
    void recurrenceOnePerCategoryConstraint() {
        ProblemDetail pd = handle("violates unique constraint \"uk_recurring_budgets_category_id\"");
        assertThat(pd.getDetail()).isEqualTo("La categoría ya tiene una recurrencia definida.");
    }

    @Test
    void monthlyBudgetPeriodConstraint() {
        ProblemDetail pd = handle("violates unique constraint \"uq_monthly_budgets_account_category_period\"");
        assertThat(pd.getDetail()).isEqualTo("Ya existe un presupuesto para esa categoría, cuenta y periodo.");
    }

    @Test
    void unknownConstraintFallsBackToGenericMessage() {
        ProblemDetail pd = handle("violates check constraint \"some_other_thing\"");
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getDetail()).isEqualTo("La operación viola una restricción de integridad de datos.");
    }

    @Test
    void nullCauseMessageFallsBackToGenericMessage() {
        ProblemDetail pd = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException(null));
        assertThat(pd.getDetail()).isEqualTo("La operación viola una restricción de integridad de datos.");
    }
}
