package com.xroig.finance.budgets.infrastructure.web;

import com.xroig.finance.budgets.application.RecurringBudgetView;
import com.xroig.finance.budgets.application.RecurringBudgetView.AmountView;
import com.xroig.finance.budgets.application.port.DeleteRecurrence;
import com.xroig.finance.budgets.application.port.FindRecurrence;
import com.xroig.finance.budgets.application.port.UpsertRecurrence;
import com.xroig.finance.budgets.application.port.UpsertRecurrence.RecurrenceCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Level-3 HTTP-contract test for {@link RecurringBudgetController}, the recurrence sub-resource
 * ({@code /api/categories/{id}/recurrence}). Pins what the {@code @WebMvcTest} slice adds: the
 * {@code {categoryId}} path-variable binding, the {@link RecurringBudgetView} JSON shape, the
 * cascading bean validation of {@link RecurringBudgetRequest} (its {@code @NotEmpty} lists and
 * the {@code @Valid} nested amounts with {@code @NotNull}/{@code @Positive}) → 400, and the
 * {@code @ResponseStatus} codes. The use-case logic lives in {@code RecurringBudgetServiceTest}.
 */
@WebMvcTest(RecurringBudgetController.class)
class RecurringBudgetControllerMvcTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private FindRecurrence findRecurrence;
    @MockitoBean private UpsertRecurrence upsertRecurrence;
    @MockitoBean private DeleteRecurrence deleteRecurrence;

    private static final String VALID_BODY = """
            {"months":[1,2],"active":true,"amounts":[{"amount":100,"validoDesde":"2024-01"}]}
            """;

    @Test
    void get_returns200WithJson() {
        when(findRecurrence.get(5L)).thenReturn(new RecurringBudgetView(
                5L, List.of(1, 2), true, List.of(new AmountView(1L, new BigDecimal("100"), "2024-01"))));

        MvcTestResult result = mvc.get().uri("/api/categories/{id}/recurrence", 5).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.categoryId").asNumber().isEqualTo(5);
        assertThat(result).bodyJson().extractingPath("$.months").asArray().containsExactly(1, 2);
        assertThat(result).bodyJson().extractingPath("$.amounts[0].validoDesde").isEqualTo("2024-01");
        assertThat(result).bodyJson().extractingPath("$.amounts[0].id").asNumber().isEqualTo(1);
    }

    @Test
    void upsert_valid_bindsPathVariableAndReturns200() {
        when(upsertRecurrence.upsert(eq(5L), any(RecurrenceCommand.class)))
                .thenReturn(new RecurringBudgetView(5L, List.of(1, 2), true, List.of()));

        assertThat(mvc.put().uri("/api/categories/{id}/recurrence", 5)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatusOk()
                .bodyJson().extractingPath("$.active").isEqualTo(true);
        verify(upsertRecurrence).upsert(eq(5L), any(RecurrenceCommand.class));
    }

    @ParameterizedTest(name = "invalid RecurringBudgetRequest → 400: {0}")
    @ValueSource(strings = {
            "{\"months\":[],\"active\":true,\"amounts\":[{\"amount\":100,\"validoDesde\":\"2024-01\"}]}",   // @NotEmpty months
            "{\"months\":[1],\"active\":true,\"amounts\":[]}",                                                // @NotEmpty amounts
            "{\"months\":[1],\"active\":true,\"amounts\":[{\"amount\":0,\"validoDesde\":\"2024-01\"}]}",      // nested @Positive
            "{\"months\":[1],\"active\":true,\"amounts\":[{\"validoDesde\":\"2024-01\"}]}",                   // nested @NotNull amount
            "{\"months\":[1],\"active\":true,\"amounts\":[{\"amount\":100}]}",                                // nested @NotNull validoDesde
    })
    void upsert_invalidBody_returns400AndDoesNotCallService(String body) {
        assertThat(mvc.put().uri("/api/categories/{id}/recurrence", 5)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verify(upsertRecurrence, never()).upsert(any(Long.class), any());
    }

    @Test
    void upsert_malformedJson_returns400() {
        assertThat(mvc.put().uri("/api/categories/{id}/recurrence", 5)
                .contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void delete_returns204AndDelegates() {
        assertThat(mvc.delete().uri("/api/categories/{id}/recurrence", 5)).hasStatus(HttpStatus.NO_CONTENT);
        verify(deleteRecurrence).delete(5L);
    }
}
