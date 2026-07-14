package com.xroig.finance.investments.infrastructure.web;

import com.xroig.finance.investments.application.InvestmentTransactionView;
import com.xroig.finance.investments.application.port.CreateInvestmentTransaction;
import com.xroig.finance.investments.application.port.CreateInvestmentTransaction.InvestmentTransactionCommand;
import com.xroig.finance.investments.application.port.DeleteInvestmentTransaction;
import com.xroig.finance.investments.application.port.FindInvestmentTransactions;
import com.xroig.finance.investments.application.port.FindInvestmentTransactions.TransactionFilter;
import com.xroig.finance.investments.application.port.UpdateInvestmentTransaction;
import com.xroig.finance.investments.domain.InvestmentTransactionType;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HTTP contract of the {@link InvestmentTransactionController} (§6): the filtered
 * listing and manual create/edit/delete of operations (RF-2) under
 * {@code /api/investments}. Sign violations and the RN-4 hard guard surface as
 * 400 {@code problem+json} through the shared domain advice; {@code @Valid}
 * rejects structurally incomplete requests before reaching the use case.
 */
@WebMvcTest(InvestmentTransactionController.class)
class InvestmentTransactionControllerMvcTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private FindInvestmentTransactions findTransactions;
    @MockitoBean private CreateInvestmentTransaction createTransaction;
    @MockitoBean private UpdateInvestmentTransaction updateTransaction;
    @MockitoBean private DeleteInvestmentTransaction deleteTransaction;

    private static InvestmentTransactionView buyView(long id) {
        return new InvestmentTransactionView(id, InvestmentTransactionType.BUY,
                LocalDate.of(2025, 3, 10), 42L, "Vanguard FTSE All-World",
                new BigDecimal("10"), new BigDecimal("100"),
                new BigDecimal("-1000"), "EUR", null, null,
                new BigDecimal("-2"), "EUR", null, null, null, "Compra manual", null);
    }

    private static final String BUY_JSON = """
            {"type":"BUY","tradeDate":"2025-03-10","securityId":42,"quantity":10,"price":100,
             "amount":-1000,"currency":"EUR","fee":-2,"description":"Compra manual"}
            """;

    @Test
    void find_passesTheFiltersThrough() {
        when(findTransactions.find(eq(7L), any(TransactionFilter.class)))
                .thenReturn(List.of(buyView(1L)));

        var result = mvc.get().uri("/api/investments/portfolios/7/transactions")
                .param("type", "BUY").param("from", "2025-01-01")
                .param("to", "2025-12-31").param("securityId", "42")
                .exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$[0].securityName")
                .isEqualTo("Vanguard FTSE All-World");
        ArgumentCaptor<TransactionFilter> filter = ArgumentCaptor.forClass(TransactionFilter.class);
        verify(findTransactions).find(eq(7L), filter.capture());
        assertThat(filter.getValue().type()).isEqualTo(InvestmentTransactionType.BUY);
        assertThat(filter.getValue().from()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(filter.getValue().to()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(filter.getValue().securityId()).isEqualTo(42L);
    }

    @Test
    void create_returns201WithTheView() {
        when(createTransaction.create(eq(7L), any(InvestmentTransactionCommand.class)))
                .thenReturn(buyView(9L));

        var result = mvc.post().uri("/api/investments/portfolios/7/transactions")
                .contentType(MediaType.APPLICATION_JSON).content(BUY_JSON).exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.id").asNumber().isEqualTo(9);
        ArgumentCaptor<InvestmentTransactionCommand> command =
                ArgumentCaptor.forClass(InvestmentTransactionCommand.class);
        verify(createTransaction).create(eq(7L), command.capture());
        assertThat(command.getValue().amount()).isEqualByComparingTo("-1000");
        assertThat(command.getValue().feeCurrency()).isNull();
    }

    @Test
    void create_missingRequiredFields_returns400WithoutCallingTheUseCase() {
        assertThat(mvc.post().uri("/api/investments/portfolios/7/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"BUY"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verify(createTransaction, never()).create(anyLong(), any());
    }

    @Test
    void create_domainViolation_returns400ProblemJson() {
        when(createTransaction.create(eq(7L), any(InvestmentTransactionCommand.class)))
                .thenThrow(new ValidationException("Venta sin posición suficiente a 2025-03-10 (RN-4)"));

        assertThat(mvc.post().uri("/api/investments/portfolios/7/transactions")
                .contentType(MediaType.APPLICATION_JSON).content(BUY_JSON))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void update_returns200WithTheView() {
        when(updateTransaction.update(eq(9L), any(InvestmentTransactionCommand.class)))
                .thenReturn(buyView(9L));

        assertThat(mvc.put().uri("/api/investments/transactions/9")
                .contentType(MediaType.APPLICATION_JSON).content(BUY_JSON))
                .hasStatusOk()
                .bodyJson().extractingPath("$.id").asNumber().isEqualTo(9);
    }

    @Test
    void delete_returns204AndDelegates() {
        assertThat(mvc.delete().uri("/api/investments/transactions/9"))
                .hasStatus(HttpStatus.NO_CONTENT);
        verify(deleteTransaction).delete(9L);
    }
}
