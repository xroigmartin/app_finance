package com.xroig.finance.investments.application;

import com.xroig.finance.investments.application.port.CreatePortfolio.CreatePortfolioCommand;
import com.xroig.finance.investments.application.port.UpdatePortfolio.UpdatePortfolioCommand;
import com.xroig.finance.investments.domain.InvestmentTransactionRepository;
import com.xroig.finance.investments.domain.Portfolio;
import com.xroig.finance.investments.domain.PortfolioId;
import com.xroig.finance.investments.domain.PortfolioRepository;
import com.xroig.finance.shared.domain.ConflictException;
import com.xroig.finance.shared.domain.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application-service tests for the portfolio use cases (H1.5), mocking the
 * outbound ports: id resolution, not-found mapping and the RN-5 deletion guard
 * (a portfolio with operations cannot be deleted). Invariants live in
 * {@code PortfolioTest}. Editing means renaming — the base currency is immutable
 * (H1.3, protects the RN-7a snapshots).
 */
@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock private PortfolioRepository portfolios;
    @Mock private InvestmentTransactionRepository transactions;

    private PortfolioService service() {
        return new PortfolioService(portfolios, transactions);
    }

    @Test
    void all_delegatesToTheRepository() {
        Portfolio stored = Portfolio.rehydrate(new PortfolioId(1L), "IB", "EUR");
        when(portfolios.findAll()).thenReturn(List.of(stored));

        assertThat(service().all()).containsExactly(stored);
    }

    @Test
    void create_savesANewAggregate() {
        when(portfolios.save(any(Portfolio.class))).thenAnswer(i -> i.getArgument(0));

        Portfolio created = service().create(new CreatePortfolioCommand("Interactive Brokers", "EUR"));

        assertThat(created.id()).isNull();
        assertThat(created.name()).isEqualTo("Interactive Brokers");
        assertThat(created.baseCurrency()).isEqualTo("EUR");
        verify(portfolios).save(any(Portfolio.class));
    }

    @Test
    void update_renames() {
        Portfolio existing = Portfolio.rehydrate(new PortfolioId(5L), "Viejo", "EUR");
        when(portfolios.findById(new PortfolioId(5L))).thenReturn(Optional.of(existing));
        when(portfolios.save(any(Portfolio.class))).thenAnswer(i -> i.getArgument(0));

        Portfolio updated = service().update(5L, new UpdatePortfolioCommand("Nuevo"));

        assertThat(updated.name()).isEqualTo("Nuevo");
        assertThat(updated.baseCurrency()).isEqualTo("EUR");
    }

    @Test
    void update_notFoundThrows() {
        when(portfolios.findById(new PortfolioId(5L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(5L, new UpdatePortfolioCommand("X")))
                .isInstanceOf(NotFoundException.class);
        verify(portfolios, never()).save(any());
    }

    @Test
    void delete_withOperationsThrowsConflict() {
        when(transactions.existsByPortfolio(new PortfolioId(5L))).thenReturn(true);

        assertThatThrownBy(() -> service().delete(5L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("operaciones");
        verify(portfolios, never()).deleteById(any());
    }

    @Test
    void delete_happyPathDeletesById() {
        when(transactions.existsByPortfolio(new PortfolioId(5L))).thenReturn(false);

        service().delete(5L);

        verify(portfolios).deleteById(new PortfolioId(5L));
    }
}
