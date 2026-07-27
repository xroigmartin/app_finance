package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.investments.domain.CurrencyMoney;
import com.xroig.finance.investments.domain.InvestmentTransaction;
import com.xroig.finance.investments.domain.InvestmentTransactionId;
import com.xroig.finance.investments.domain.InvestmentTransactionType;
import com.xroig.finance.investments.domain.Portfolio;
import com.xroig.finance.investments.domain.PortfolioId;
import com.xroig.finance.investments.domain.Quantity;
import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.SecurityId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adapter test (Level 2) against real PostgreSQL: the
 * {@link InvestmentTransactionPersistenceAdapter} +
 * {@link InvestmentTransactionJpaMapper} round-trip preserves the full
 * conditional field set of the operation (counter leg, own-currency fee/tax,
 * RN-7a snapshot, external id), the RN-5/RN-10 existence checks work against
 * real rows, and the idempotency backstop — {@code UNIQUE (portfolio_id,
 * external_id)} in the V7 migration — rejects a duplicate while allowing many
 * manual (null) external ids.
 */
@Import({InvestmentTransactionPersistenceAdapter.class, InvestmentTransactionJpaMapper.class,
        PortfolioPersistenceAdapter.class, PortfolioJpaMapper.class,
        SecurityPersistenceAdapter.class, SecurityJpaMapper.class})
class InvestmentTransactionPersistenceAdapterTest extends PostgresTestBase {

    @Autowired private InvestmentTransactionPersistenceAdapter adapter;
    @Autowired private InvestmentTransactionJpaRepository jpa;
    @Autowired private PortfolioPersistenceAdapter portfolios;
    @Autowired private SecurityPersistenceAdapter securities;

    private PortfolioId portfolioId;
    private SecurityId securityId;

    @BeforeEach
    void persistParents() {
        portfolioId = portfolios.save(Portfolio.create("IBKR", "EUR")).id();
        securityId = securities.save(Security.create(
                "IE00BK5BQT80", "EUR", "Vanguard FTSE All-World", "VWCE", "ETF", "AEB", null)).id();
    }

    @Test
    void save_roundTripsABuyWithEveryField() {
        InvestmentTransaction saved = adapter.save(InvestmentTransaction.builder()
                .portfolio(portfolioId)
                .security(securityId)
                .type(InvestmentTransactionType.BUY)
                .tradeDate(LocalDate.of(2024, 3, 15))
                .quantity(Quantity.of("2.303"))
                .price("104.52")
                .amount(CurrencyMoney.of("-240.71", "EUR"))
                .fee(CurrencyMoney.of("-1.25", "EUR"))
                .fxRateToBase("1")
                .description("Compra VWCE")
                .externalId("ORD-123456")
                .build());

        assertThat(saved.id()).isNotNull();
        assertThat(adapter.findById(saved.id())).hasValueSatisfying(t -> {
            assertThat(t.portfolioId()).isEqualTo(portfolioId);
            assertThat(t.securityId()).isEqualTo(securityId);
            assertThat(t.type()).isEqualTo(InvestmentTransactionType.BUY);
            assertThat(t.tradeDate()).isEqualTo(LocalDate.of(2024, 3, 15));
            assertThat(t.quantity()).isEqualTo(Quantity.of("2.303"));
            assertThat(t.price()).isEqualByComparingTo("104.52");
            assertThat(t.amount()).isEqualTo(CurrencyMoney.of("-240.71", "EUR"));
            assertThat(t.fee()).isEqualTo(CurrencyMoney.of("-1.25", "EUR"));
            assertThat(t.tax()).isNull();
            assertThat(t.counterAmount()).isNull();
            assertThat(t.fxRateToBase()).isEqualByComparingTo("1");
            assertThat(t.description()).isEqualTo("Compra VWCE");
            assertThat(t.externalId()).isEqualTo("ORD-123456");
        });
    }

    @Test
    void save_roundTripsAnFxTradeWithCounterLegAndOwnCurrencyFee() {
        InvestmentTransaction saved = adapter.save(InvestmentTransaction.builder()
                .portfolio(portfolioId)
                .type(InvestmentTransactionType.FX_TRADE)
                .tradeDate(LocalDate.of(2024, 5, 2))
                .amount(CurrencyMoney.of("-1000", "EUR"))
                .counterAmount(CurrencyMoney.of("1082.30", "USD"))
                .fee(CurrencyMoney.of("-2", "EUR"))
                .externalId("ORD-778899")
                .build());

        assertThat(adapter.findById(saved.id())).hasValueSatisfying(t -> {
            assertThat(t.securityId()).isNull();
            assertThat(t.amount()).isEqualTo(CurrencyMoney.of("-1000", "EUR"));
            assertThat(t.counterAmount()).isEqualTo(CurrencyMoney.of("1082.30", "USD"));
            assertThat(t.fee()).isEqualTo(CurrencyMoney.of("-2", "EUR"));
            assertThat(t.quantity()).isNull();
            assertThat(t.price()).isNull();
        });
    }

    @Test
    void save_roundTripsADividendWithOwnCurrencyTax() {
        InvestmentTransaction saved = adapter.save(InvestmentTransaction.builder()
                .portfolio(portfolioId)
                .security(securityId)
                .type(InvestmentTransactionType.DIVIDEND)
                .tradeDate(LocalDate.of(2024, 6, 20))
                .amount(CurrencyMoney.of("55.10", "USD"))
                .tax(CurrencyMoney.of("-8.27", "EUR"))
                .fxRateToBase("0.92345678")
                .externalId("CT-445566")
                .build());

        assertThat(adapter.findById(saved.id())).hasValueSatisfying(t -> {
            assertThat(t.amount()).isEqualTo(CurrencyMoney.of("55.10", "USD"));
            assertThat(t.tax()).isEqualTo(CurrencyMoney.of("-8.27", "EUR"));
            assertThat(t.fxRateToBase()).isEqualByComparingTo("0.92345678");
        });
    }

    @Test
    void findByPortfolio_returnsOnlyItsOperationsOrderedByTradeDate() {
        PortfolioId other = portfolios.save(Portfolio.create("Otra cartera", "EUR")).id();
        adapter.save(deposit(portfolioId, LocalDate.of(2024, 2, 1), "200"));
        adapter.save(deposit(portfolioId, LocalDate.of(2024, 1, 1), "100"));
        adapter.save(deposit(other, LocalDate.of(2024, 1, 15), "999"));

        assertThat(adapter.findByPortfolio(portfolioId))
                .extracting(InvestmentTransaction::tradeDate)
                .containsExactly(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1));
    }

    @Test
    void existenceChecks_matchRealRows() {
        adapter.save(InvestmentTransaction.builder()
                .portfolio(portfolioId)
                .security(securityId)
                .type(InvestmentTransactionType.BUY)
                .tradeDate(LocalDate.of(2024, 3, 15))
                .quantity(Quantity.of("1"))
                .amount(CurrencyMoney.of("-100", "EUR"))
                .externalId("ORD-1")
                .build());
        PortfolioId emptyPortfolio = portfolios.save(Portfolio.create("Vacía", "EUR")).id();
        SecurityId unusedSecurity = securities.save(Security.create(
                "US0378331005", "USD", "Apple Inc", null, null, null, null)).id();

        assertThat(adapter.existsByPortfolio(portfolioId)).isTrue();
        assertThat(adapter.existsByPortfolio(emptyPortfolio)).isFalse();
        assertThat(adapter.existsBySecurity(securityId)).isTrue();
        assertThat(adapter.existsBySecurity(unusedSecurity)).isFalse();
        assertThat(adapter.existsByPortfolioAndExternalId(portfolioId, "ORD-1")).isTrue();
        assertThat(adapter.existsByPortfolioAndExternalId(portfolioId, "ORD-2")).isFalse();
        assertThat(adapter.existsByPortfolioAndExternalId(emptyPortfolio, "ORD-1")).isFalse();
    }

    @Test
    void duplicateExternalIdInSamePortfolio_violatesTheUniqueConstraint() {
        adapter.save(deposit(portfolioId, LocalDate.of(2024, 1, 1), "100", "CT-1"));

        assertThatThrownBy(() -> {
            adapter.save(deposit(portfolioId, LocalDate.of(2024, 2, 1), "200", "CT-1"));
            jpa.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameExternalIdInAnotherPortfolio_isAllowed() {
        PortfolioId other = portfolios.save(Portfolio.create("Otra", "EUR")).id();
        adapter.save(deposit(portfolioId, LocalDate.of(2024, 1, 1), "100", "CT-1"));
        adapter.save(deposit(other, LocalDate.of(2024, 1, 1), "100", "CT-1"));
        jpa.flush();

        assertThat(adapter.existsByPortfolioAndExternalId(portfolioId, "CT-1")).isTrue();
        assertThat(adapter.existsByPortfolioAndExternalId(other, "CT-1")).isTrue();
    }

    @Test
    void manualEntriesWithoutExternalId_doNotCollide() {
        adapter.save(deposit(portfolioId, LocalDate.of(2024, 1, 1), "100"));
        adapter.save(deposit(portfolioId, LocalDate.of(2024, 2, 1), "200"));
        jpa.flush();

        assertThat(adapter.findByPortfolio(portfolioId)).hasSize(2);
    }

    @Test
    void search_paginatesAndFiltersAtTheDatabase() {
        adapter.save(deposit(portfolioId, LocalDate.of(2024, 1, 1), "100"));
        adapter.save(deposit(portfolioId, LocalDate.of(2024, 2, 1), "200"));
        adapter.save(deposit(portfolioId, LocalDate.of(2024, 3, 1), "300"));
        PortfolioId other = portfolios.save(Portfolio.create("Otra cartera", "EUR")).id();
        adapter.save(deposit(other, LocalDate.of(2024, 2, 15), "999"));

        var firstPage = adapter.search(portfolioId, null, null, null, null, 0, 2);
        assertThat(firstPage.content()).extracting(InvestmentTransaction::tradeDate)
                .containsExactly(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 2, 1));
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);

        var secondPage = adapter.search(portfolioId, null, null, null, null, 1, 2);
        assertThat(secondPage.content()).extracting(InvestmentTransaction::tradeDate)
                .containsExactly(LocalDate.of(2024, 1, 1));
    }

    @Test
    void search_filtersByTypeDateRangeAndSecurity() {
        adapter.save(deposit(portfolioId, LocalDate.of(2024, 1, 10), "100"));
        adapter.save(InvestmentTransaction.builder()
                .portfolio(portfolioId).security(securityId)
                .type(InvestmentTransactionType.BUY).tradeDate(LocalDate.of(2024, 2, 10))
                .quantity(Quantity.of("1")).amount(CurrencyMoney.of("-100", "EUR")).build());
        adapter.save(InvestmentTransaction.builder()
                .portfolio(portfolioId).security(securityId)
                .type(InvestmentTransactionType.BUY).tradeDate(LocalDate.of(2024, 6, 10))
                .quantity(Quantity.of("1")).amount(CurrencyMoney.of("-50", "EUR")).build());

        var byType = adapter.search(portfolioId, InvestmentTransactionType.BUY, null, null, null, 0, 10);
        assertThat(byType.content()).hasSize(2);

        var byRange = adapter.search(portfolioId, InvestmentTransactionType.BUY,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 1), null, 0, 10);
        assertThat(byRange.content()).extracting(InvestmentTransaction::tradeDate)
                .containsExactly(LocalDate.of(2024, 2, 10));

        var bySecurity = adapter.search(portfolioId, null, null, null, securityId, 0, 10);
        assertThat(bySecurity.content()).hasSize(2);
    }

    @Test
    void deleteById_removesTheRow() {
        InvestmentTransactionId id = adapter.save(
                deposit(portfolioId, LocalDate.of(2024, 1, 1), "100")).id();

        adapter.deleteById(id);

        assertThat(jpa.existsById(id.value())).isFalse();
    }

    private InvestmentTransaction deposit(PortfolioId portfolio, LocalDate date, String amount) {
        return deposit(portfolio, date, amount, null);
    }

    private InvestmentTransaction deposit(PortfolioId portfolio, LocalDate date, String amount, String externalId) {
        return InvestmentTransaction.builder()
                .portfolio(portfolio)
                .type(InvestmentTransactionType.DEPOSIT)
                .tradeDate(date)
                .amount(CurrencyMoney.of(new BigDecimal(amount), "EUR"))
                .externalId(externalId)
                .build();
    }
}
