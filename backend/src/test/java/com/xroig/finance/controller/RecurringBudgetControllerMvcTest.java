package com.xroig.finance.controller;

import com.xroig.finance.dto.RecurringBudgetDtos.AmountResponse;
import com.xroig.finance.dto.RecurringBudgetDtos.RecurringBudgetRequest;
import com.xroig.finance.dto.RecurringBudgetDtos.RecurringBudgetResponse;
import com.xroig.finance.service.RecurringBudgetService;
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
 * Level-3 HTTP-contract test (stage M5) for {@link RecurringBudgetController}, the
 * recurrence sub-resource ({@code /api/categories/{id}/recurrence}). The upsert/
 * reconciliation logic is in {@code RecurringBudgetServiceTest}; here we pin what
 * the {@code @WebMvcTest} slice adds: the {@code {categoryId}} path-variable
 * binding, the {@code RecurringBudgetResponse} JSON shape, the cascading bean
 * validation of {@code RecurringBudgetRequest} (its {@code @NotEmpty} lists and the
 * {@code @Valid} nested {@code AmountRequest} with {@code @NotNull}/{@code @Positive})
 * → 400, and the {@code @ResponseStatus} codes.
 */
@WebMvcTest(RecurringBudgetController.class)
class RecurringBudgetControllerMvcTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private RecurringBudgetService recurringBudgetService;

    private static final String VALID_BODY = """
            {"months":[1,2],"active":true,"amounts":[{"amount":100,"validoDesde":"2024-01"}]}
            """;

    @Test
    void get_returns200WithJson() {
        when(recurringBudgetService.get(5L)).thenReturn(new RecurringBudgetResponse(
                5L, List.of(1, 2), true, List.of(new AmountResponse(1L, new BigDecimal("100"), "2024-01"))));

        MvcTestResult result = mvc.get().uri("/api/categories/{id}/recurrence", 5).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.categoryId").asNumber().isEqualTo(5);
        assertThat(result).bodyJson().extractingPath("$.months").asArray().containsExactly(1, 2);
        assertThat(result).bodyJson().extractingPath("$.amounts[0].validoDesde").isEqualTo("2024-01");
    }

    @Test
    void upsert_valid_bindsPathVariableAndReturns200() {
        when(recurringBudgetService.upsert(eq(5L), any(RecurringBudgetRequest.class)))
                .thenReturn(new RecurringBudgetResponse(5L, List.of(1, 2), true, List.of()));

        assertThat(mvc.put().uri("/api/categories/{id}/recurrence", 5)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatusOk()
                .bodyJson().extractingPath("$.active").isEqualTo(true);
        verify(recurringBudgetService).upsert(eq(5L), any(RecurringBudgetRequest.class));
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
        verify(recurringBudgetService, never()).upsert(any(), any());
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
        verify(recurringBudgetService).delete(5L);
    }
}
