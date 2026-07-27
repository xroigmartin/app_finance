package com.xroig.finance.reporting.infrastructure.web;

import com.xroig.finance.reporting.application.MovementView;
import com.xroig.finance.reporting.application.port.FindMovements;
import com.xroig.finance.shared.domain.Page;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HTTP-contract test ({@code @WebMvcTest}) for the read-only {@link MovementController}:
 * the combined "Movimientos" feed under {@code /api/movements}, delegating to a mocked
 * {@link FindMovements}. The 1970/2999 wide-range default for an omitted {@code from}/
 * {@code to} mirrors {@code TransactionController}/{@code TransferController}.
 */
@WebMvcTest(MovementController.class)
class MovementControllerMvcTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private FindMovements findMovements;

    @Test
    void find_bindsFiltersAndPaging() {
        when(findMovements.findMovements(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), 1L, 2L, 1, 5))
                .thenReturn(new Page<>(List.of(), 1, 5, 0));

        assertThat(mvc.get().uri("/api/movements")
                .param("from", "2024-01-01").param("to", "2024-12-31")
                .param("accountId", "1").param("categoryId", "2")
                .param("page", "1").param("size", "5"))
                .hasStatusOk();

        verify(findMovements).findMovements(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), 1L, 2L, 1, 5);
    }

    @Test
    void find_withoutParams_usesWideDateRangeAndDefaultPaging() {
        when(findMovements.findMovements(LocalDate.of(1970, 1, 1), LocalDate.of(2999, 12, 31), null, null, 0, 25))
                .thenReturn(new Page<>(List.of(), 0, 25, 0));

        assertThat(mvc.get().uri("/api/movements")).hasStatusOk();

        verify(findMovements).findMovements(eq(LocalDate.of(1970, 1, 1)), eq(LocalDate.of(2999, 12, 31)),
                isNull(), isNull(), eq(0), eq(25));
    }

    @Test
    void find_returnsThePageShapeAsJson() {
        MovementView view = new MovementView("tx", LocalDate.of(2024, 3, 10), 9L, null, null);
        when(findMovements.findMovements(LocalDate.of(1970, 1, 1), LocalDate.of(2999, 12, 31), null, null, 0, 25))
                .thenReturn(new Page<>(List.of(view), 0, 25, 1));

        var result = mvc.get().uri("/api/movements").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.content[0].source").isEqualTo("tx");
        assertThat(result).bodyJson().extractingPath("$.totalElements").isEqualTo(1);
    }
}
