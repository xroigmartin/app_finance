package com.xroig.finance.controller;

import com.xroig.finance.dto.RecurringBudgetDtos.RecurringBudgetRequest;
import com.xroig.finance.dto.RecurringBudgetDtos.RecurringBudgetResponse;
import com.xroig.finance.service.RecurringBudgetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RecurringBudgetController} with a mocked service (stage
 * E4 gap fill): the sub-resource endpoints delegate to the service.
 */
@ExtendWith(MockitoExtension.class)
class RecurringBudgetControllerTest {

    @Mock private RecurringBudgetService recurringBudgetService;
    @InjectMocks private RecurringBudgetController controller;

    @Test
    void get_delegatesToService() {
        RecurringBudgetResponse expected = new RecurringBudgetResponse(7L, List.of(1), true, List.of());
        when(recurringBudgetService.get(7L)).thenReturn(expected);

        assertThat(controller.get(7L)).isSameAs(expected);
    }

    @Test
    void upsert_delegatesToService() {
        RecurringBudgetRequest request = new RecurringBudgetRequest(List.of(1), true, List.of());
        RecurringBudgetResponse expected = new RecurringBudgetResponse(7L, List.of(1), true, List.of());
        when(recurringBudgetService.upsert(7L, request)).thenReturn(expected);

        assertThat(controller.upsert(7L, request)).isSameAs(expected);
    }

    @Test
    void delete_delegatesToService() {
        controller.delete(7L);
        verify(recurringBudgetService).delete(7L);
    }
}
