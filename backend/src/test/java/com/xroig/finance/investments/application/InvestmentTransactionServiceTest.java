package com.xroig.finance.investments.application;

import com.xroig.finance.investments.application.port.CreateInvestmentTransaction.InvestmentTransactionCommand;
import com.xroig.finance.investments.application.port.FindInvestmentTransactions.TransactionFilter;
import com.xroig.finance.investments.domain.CurrencyMoney;
import com.xroig.finance.investments.domain.InvestmentTransaction;
import com.xroig.finance.investments.domain.InvestmentTransactionId;
import com.xroig.finance.investments.domain.InvestmentTransactionRepository;
import com.xroig.finance.investments.domain.InvestmentTransactionType;
import com.xroig.finance.investments.domain.Portfolio;
import com.xroig.finance.investments.domain.PortfolioId;
import com.xroig.finance.investments.domain.PortfolioRepository;
import com.xroig.finance.investments.domain.Quantity;
import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.SecurityId;
import com.xroig.finance.investments.domain.SecurityRepository;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.Page;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application tests for the manual operation use cases (H2.3, RF-2), mocking the
 * outbound ports: creation delegates the §3 sign invariants to the aggregate
 * (violation → {@code ValidationException}, §8), a manual sale beyond the held
 * position at its date is a hard error (RN-4's manual side — the import side is a
 * warning), editing rebuilds the aggregate preserving identity and
 * {@code external_id}, and the listing delegates the filtered, paginated search
 * to the repository, only attaching the instrument name.
 */
@ExtendWith(MockitoExtension.class)
class InvestmentTransactionServiceTest {

    private static final PortfolioId PORTFOLIO_ID = new PortfolioId(7L);
    private static final SecurityId SECURITY_ID = new SecurityId(42L);
    private static final LocalDate MARCH_10 = LocalDate.of(2025, 3, 10);

    @Mock private PortfolioRepository portfolios;
    @Mock private SecurityRepository securities;
    @Mock private InvestmentTransactionRepository transactions;

    private InvestmentTransactionService service() {
        return new InvestmentTransactionService(portfolios, securities, transactions);
    }

    private void givenPortfolio() {
        when(portfolios.findById(PORTFOLIO_ID))
                .thenReturn(Optional.of(Portfolio.rehydrate(PORTFOLIO_ID, "IBKR", "EUR")));
    }

    private void givenSecurity() {
        when(securities.findById(SECURITY_ID)).thenReturn(Optional.of(Security.rehydrate(
                SECURITY_ID, "IE00BK5BQT80", "EUR", "Vanguard FTSE All-World", "VWCE", null, null, null)));
    }

    private static InvestmentTransactionCommand buyCommand(String quantity, String amount) {
        return new InvestmentTransactionCommand(SECURITY_ID.value(), InvestmentTransactionType.BUY,
                MARCH_10, new BigDecimal(quantity), new BigDecimal("100"),
                new BigDecimal(amount), "EUR", null, null,
                new BigDecimal("-2"), null, null, null, null, "Compra manual");
    }

    @Test
    void create_buildsValidatesAndSavesTheOperation() {
        givenPortfolio();
        givenSecurity();
        when(transactions.save(any(InvestmentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        InvestmentTransactionView view = service().create(7L, buyCommand("10", "-1000"));

        ArgumentCaptor<InvestmentTransaction> saved = ArgumentCaptor.forClass(InvestmentTransaction.class);
        verify(transactions).save(saved.capture());
        InvestmentTransaction tx = saved.getValue();
        assertThat(tx.portfolioId()).isEqualTo(PORTFOLIO_ID);
        assertThat(tx.securityId()).isEqualTo(SECURITY_ID);
        assertThat(tx.amount()).isEqualTo(CurrencyMoney.of("-1000", "EUR"));
        assertThat(tx.fee()).isEqualTo(CurrencyMoney.of("-2", "EUR"));
        assertThat(tx.externalId()).isNull(); // los apuntes manuales no llevan external_id (RN-10)
        assertThat(view.securityName()).isEqualTo("Vanguard FTSE All-World");
        assertThat(view.type()).isEqualTo(InvestmentTransactionType.BUY);
    }

    private static InvestmentTransactionCommand sellCommand(String quantity, String amount) {
        return new InvestmentTransactionCommand(SECURITY_ID.value(), InvestmentTransactionType.SELL,
                MARCH_10, new BigDecimal(quantity), new BigDecimal("100"),
                new BigDecimal(amount), "EUR", null, null, null, null, null, null, null, null);
    }

    private static InvestmentTransaction storedBuy(long id, LocalDate date, String quantity) {
        return InvestmentTransaction.builder()
                .portfolio(PORTFOLIO_ID).security(SECURITY_ID)
                .type(InvestmentTransactionType.BUY).tradeDate(date)
                .quantity(Quantity.of(quantity))
                .amount(CurrencyMoney.of("-1000", "EUR"))
                .rehydrate(new InvestmentTransactionId(id));
    }

    @Test
    void create_sellWithinTheHeldPositionIsAccepted() {
        givenPortfolio();
        givenSecurity();
        when(transactions.findByPortfolio(PORTFOLIO_ID))
                .thenReturn(List.of(storedBuy(1L, MARCH_10.minusDays(5), "10")));
        when(transactions.save(any(InvestmentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        InvestmentTransactionView view = service().create(7L, sellCommand("-10", "1000"));

        assertThat(view.quantity()).isEqualByComparingTo("-10");
    }

    @Test
    void create_sellBeyondTheHeldPositionIsRejected() {
        givenPortfolio();
        givenSecurity();
        when(transactions.findByPortfolio(PORTFOLIO_ID))
                .thenReturn(List.of(storedBuy(1L, MARCH_10.minusDays(5), "10")));

        assertThatThrownBy(() -> service().create(7L, sellCommand("-11", "1100")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("RN-4");

        verify(transactions, never()).save(any());
    }

    @Test
    void create_sellOnlyCountsPositionUpToItsDate() {
        givenPortfolio();
        givenSecurity();
        // La compra es posterior a la fecha de la venta: no cubre la posición.
        when(transactions.findByPortfolio(PORTFOLIO_ID))
                .thenReturn(List.of(storedBuy(1L, MARCH_10.plusDays(5), "10")));

        assertThatThrownBy(() -> service().create(7L, sellCommand("-10", "1000")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void update_sellExcludesTheEditedRowFromTheHeldPosition() {
        givenSecurity();
        InvestmentTransaction editedSell = InvestmentTransaction.builder()
                .portfolio(PORTFOLIO_ID).security(SECURITY_ID)
                .type(InvestmentTransactionType.SELL).tradeDate(MARCH_10)
                .quantity(Quantity.of("-5")).amount(CurrencyMoney.of("500", "EUR"))
                .rehydrate(new InvestmentTransactionId(9L));
        when(transactions.findById(new InvestmentTransactionId(9L))).thenReturn(Optional.of(editedSell));
        when(transactions.findByPortfolio(PORTFOLIO_ID))
                .thenReturn(List.of(storedBuy(1L, MARCH_10.minusDays(5), "10"), editedSell));
        when(transactions.save(any(InvestmentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        // Ampliar la venta a -10 solo cabe si la propia fila editada (-5) no descuenta.
        InvestmentTransactionView view = service().update(9L, sellCommand("-10", "1000"));

        assertThat(view.quantity()).isEqualByComparingTo("-10");
        assertThat(view.id()).isEqualTo(9L);
    }

    @Test
    void update_preservesIdentityAndExternalId() {
        givenSecurity();
        InvestmentTransaction imported = InvestmentTransaction.builder()
                .portfolio(PORTFOLIO_ID).security(SECURITY_ID)
                .type(InvestmentTransactionType.BUY).tradeDate(MARCH_10)
                .quantity(Quantity.of("10")).amount(CurrencyMoney.of("-1000", "EUR"))
                .externalId("ORD-1")
                .rehydrate(new InvestmentTransactionId(9L));
        when(transactions.findById(new InvestmentTransactionId(9L))).thenReturn(Optional.of(imported));
        when(transactions.save(any(InvestmentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        service().update(9L, buyCommand("12", "-1200"));

        ArgumentCaptor<InvestmentTransaction> saved = ArgumentCaptor.forClass(InvestmentTransaction.class);
        verify(transactions).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(new InvestmentTransactionId(9L));
        assertThat(saved.getValue().externalId()).isEqualTo("ORD-1");
        assertThat(saved.getValue().quantity()).isEqualTo(Quantity.of("12"));
    }

    @Test
    void update_unknownOperationThrowsNotFound() {
        when(transactions.findById(new InvestmentTransactionId(9L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(9L, buyCommand("10", "-1000")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_delegatesToTheRepository() {
        service().delete(9L);

        verify(transactions).deleteById(new InvestmentTransactionId(9L));
    }

    @Test
    void find_delegatesFilterAndPagingToTheRepositoryAndAttachesSecurityNames() {
        givenPortfolio();
        when(securities.findAll()).thenReturn(List.of(Security.rehydrate(
                SECURITY_ID, "IE00BK5BQT80", "EUR", "Vanguard FTSE All-World", null, null, null, null)));
        InvestmentTransaction recent = storedBuy(2L, MARCH_10, "10");
        when(transactions.search(PORTFOLIO_ID, InvestmentTransactionType.BUY,
                MARCH_10.minusDays(1), MARCH_10, SECURITY_ID, 1, 10))
                .thenReturn(new Page<>(List.of(recent), 1, 10, 42));

        var result = service().find(7L, new TransactionFilter(InvestmentTransactionType.BUY,
                MARCH_10.minusDays(1), MARCH_10, SECURITY_ID.value()), 1, 10);

        assertThat(result.content()).extracting(InvestmentTransactionView::id).containsExactly(2L);
        assertThat(result.content().getFirst().securityName()).isEqualTo("Vanguard FTSE All-World");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(42);
    }

    @Test
    void find_defaultsUnsetFilterFieldsToNullOnTheRepositoryCall() {
        givenPortfolio();
        when(transactions.search(PORTFOLIO_ID, null, null, null, null, 0, 25))
                .thenReturn(new Page<>(List.of(), 0, 25, 0));

        var result = service().find(7L, TransactionFilter.none(), 0, 25);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void find_rejectsNegativePageOrNonPositiveSize() {
        givenPortfolio();

        assertThatThrownBy(() -> service().find(7L, TransactionFilter.none(), -1, 10))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service().find(7L, TransactionFilter.none(), 0, 0))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_signViolationThrowsValidation() {
        givenPortfolio();
        givenSecurity();

        assertThatThrownBy(() -> service().create(7L, buyCommand("10", "1000"))) // BUY con amount > 0
                .isInstanceOf(ValidationException.class);

        verify(transactions, never()).save(any());
    }

    @Test
    void create_unknownPortfolioThrowsNotFound() {
        when(portfolios.findById(PORTFOLIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(7L, buyCommand("10", "-1000")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_unknownSecurityThrowsNotFound() {
        givenPortfolio();
        when(securities.findById(SECURITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(7L, buyCommand("10", "-1000")))
                .isInstanceOf(NotFoundException.class);
    }
}
