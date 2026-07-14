package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.investments.application.IncomeView;
import com.xroig.finance.investments.application.InvestmentsSummaryView;
import com.xroig.finance.investments.application.PortfolioSummaryView;
import com.xroig.finance.investments.application.PositionView;
import com.xroig.finance.investments.application.ValuationHistoryView;
import com.xroig.finance.investments.domain.CurrencyMoney;
import com.xroig.finance.investments.domain.ExchangeRate;
import com.xroig.finance.investments.domain.InvestmentTransaction;
import com.xroig.finance.investments.domain.InvestmentTransactionType;
import com.xroig.finance.investments.domain.Portfolio;
import com.xroig.finance.investments.domain.PortfolioId;
import com.xroig.finance.investments.domain.PriceQuote;
import com.xroig.finance.investments.domain.Quantity;
import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.SecurityId;
import com.xroig.finance.shared.domain.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Read-side adapter test (Level 2) against real PostgreSQL: the
 * {@link InvestmentQueryAdapter} assembles the CQRS views from the persisted
 * graph — positions valued at the latest quote ≤ today (RN-6, at cost with a
 * notice when no quote exists), base-currency conversion with the RN-7 dual
 * mechanism (snapshots for fixed amounts, rate table for valuation), the
 * portfolio summary KPIs, the valuation-history series and the global multi-
 * portfolio summary in EUR (RF-10).
 */
@Import({InvestmentQueryAdapter.class,
        PortfolioPersistenceAdapter.class, PortfolioJpaMapper.class,
        SecurityPersistenceAdapter.class, SecurityJpaMapper.class,
        InvestmentTransactionPersistenceAdapter.class, InvestmentTransactionJpaMapper.class,
        PriceQuotePersistenceAdapter.class, ExchangeRatePersistenceAdapter.class})
class InvestmentQueryAdapterTest extends PostgresTestBase {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private InvestmentQueryAdapter adapter;
    @Autowired private PortfolioPersistenceAdapter portfolios;
    @Autowired private SecurityPersistenceAdapter securities;
    @Autowired private InvestmentTransactionPersistenceAdapter transactions;
    @Autowired private PriceQuotePersistenceAdapter quotes;
    @Autowired private ExchangeRatePersistenceAdapter rates;

    // ---- positions ----

    @Test
    void positions_valuesAtTheLatestQuoteAndComputesLatentPnl() {
        PortfolioId portfolio = portfolios.save(Portfolio.create("IBKR", "EUR")).id();
        SecurityId vwce = securities.save(Security.create(
                "IE00BK5BQT80", "EUR", "Vanguard FTSE All-World", "VWCE", "ETF", null, null)).id();
        // Compra 10 títulos: coste capitalizado 1000 + 2 = 1002 EUR (RN-3).
        transactions.save(buy(portfolio, vwce, TODAY.minusDays(30), "10", "100", "-1000", "-2"));
        quotes.upsert(PriceQuote.of(vwce, TODAY.minusDays(10), "90"));
        quotes.upsert(PriceQuote.of(vwce, TODAY.minusDays(3), "110"));

        var positions = adapter.positions(portfolio.value());

        assertThat(positions).hasSize(1);
        PositionView view = positions.getFirst();
        assertThat(view.securityId()).isEqualTo(vwce.value());
        assertThat(view.isin()).isEqualTo("IE00BK5BQT80");
        assertThat(view.name()).isEqualTo("Vanguard FTSE All-World");
        assertThat(view.ticker()).isEqualTo("VWCE");
        assertThat(view.currency()).isEqualTo("EUR");
        assertThat(view.quantity()).isEqualByComparingTo("10");
        assertThat(view.costBasis()).isEqualByComparingTo("1002");
        assertThat(view.averageCost()).isEqualByComparingTo("100.2");
        assertThat(view.marketPrice()).isEqualByComparingTo("110");
        assertThat(view.quoteDate()).isEqualTo(TODAY.minusDays(3));
        assertThat(view.marketValue()).isEqualByComparingTo("1100");
        assertThat(view.latentPnl()).isEqualByComparingTo("98");
        assertThat(view.latentPnlPercent()).isEqualByComparingTo("9.78"); // 98/1002
        assertThat(view.pricedAtCost()).isFalse();
    }

    @Test
    void positions_withoutQuoteShowAtCostWithNotice() {
        PortfolioId portfolio = portfolios.save(Portfolio.create("IBKR", "EUR")).id();
        SecurityId security = persistSecurity("IE00B4L5Y983", "EUR", "iShares Core MSCI World");
        transactions.save(buy(portfolio, security, TODAY.minusDays(5), "4", "50", "-200", null));

        var positions = adapter.positions(portfolio.value());

        PositionView view = positions.getFirst();
        assertThat(view.pricedAtCost()).isTrue();
        assertThat(view.marketPrice()).isNull();
        assertThat(view.quoteDate()).isNull();
        assertThat(view.marketValue()).isEqualByComparingTo("200"); // a coste (RN-6)
        assertThat(view.latentPnl()).isEqualByComparingTo("0");
    }

    @Test
    void positions_convertForeignCurrencyValueWithTheRateTable() {
        PortfolioId portfolio = portfolios.save(Portfolio.create("IBKR", "EUR")).id();
        SecurityId apple = persistSecurity("US0378331005", "USD", "Apple Inc");
        // Compra en USD con snapshot RN-7a: coste 500 USD × 0.9 = 450 EUR.
        transactions.save(InvestmentTransaction.builder()
                .portfolio(portfolio).security(apple)
                .type(InvestmentTransactionType.BUY)
                .tradeDate(TODAY.minusDays(20))
                .quantity(Quantity.of("5")).price("100")
                .amount(CurrencyMoney.of("-500", "USD"))
                .fxRateToBase("0.9")
                .build());
        quotes.upsert(PriceQuote.of(apple, TODAY.minusDays(2), "120"));
        // Valoración RN-7b: 5 × 120 = 600 USD × 0.92 = 552 EUR.
        rates.upsert(ExchangeRate.toEur(TODAY.minusDays(2), "USD", "0.92"));

        PositionView view = adapter.positions(portfolio.value()).getFirst();

        assertThat(view.costBasis()).isEqualByComparingTo("450");
        assertThat(view.marketValue()).isEqualByComparingTo("552");
        assertThat(view.latentPnl()).isEqualByComparingTo("102");
    }

    @Test
    void positions_excludeClosedOnesAndKeepNegativeOnes() {
        PortfolioId portfolio = portfolios.save(Portfolio.create("IBKR", "EUR")).id();
        SecurityId closed = persistSecurity("IE00BK5BQT80", "EUR", "Cerrada");
        SecurityId negative = persistSecurity("US0378331005", "EUR", "Sin histórico");
        transactions.save(buy(portfolio, closed, TODAY.minusDays(10), "10", "100", "-1000", null));
        transactions.save(sell(portfolio, closed, TODAY.minusDays(5), "-10", "100", "1000"));
        transactions.save(sell(portfolio, negative, TODAY.minusDays(5), "-3", "10", "30"));

        var positions = adapter.positions(portfolio.value());

        assertThat(positions).extracting(PositionView::securityId)
                .containsExactly(negative.value());
        assertThat(positions.getFirst().quantity()).isEqualByComparingTo("-3");
    }

    @Test
    void positions_weightIsTheShareOfTheTotalIncludingCash() {
        PortfolioId portfolio = portfolios.save(Portfolio.create("IBKR", "EUR")).id();
        SecurityId security = persistSecurity("IE00BK5BQT80", "EUR", "VWCE");
        transactions.save(deposit(portfolio, TODAY.minusDays(30), "1000"));
        transactions.save(buy(portfolio, security, TODAY.minusDays(20), "6", "100", "-600", null));
        quotes.upsert(PriceQuote.of(security, TODAY.minusDays(1), "100"));

        PositionView view = adapter.positions(portfolio.value()).getFirst();

        // Valor posición 600, efectivo 400 → peso 60 %.
        assertThat(view.weight()).isEqualByComparingTo("60");
    }

    @Test
    void positions_ofAMissingPortfolio_throwNotFound() {
        assertThatThrownBy(() -> adapter.positions(-1L)).isInstanceOf(NotFoundException.class);
    }

    // ---- summary ----

    @Test
    void summary_aggregatesValueContributionsCashAndDividends() {
        PortfolioId portfolio = portfolios.save(Portfolio.create("IBKR", "EUR")).id();
        SecurityId security = persistSecurity("IE00BK5BQT80", "EUR", "VWCE");
        transactions.save(deposit(portfolio, TODAY.minusDays(40), "2000"));
        transactions.save(withdrawal(portfolio, TODAY.minusDays(35), "-500"));
        transactions.save(buy(portfolio, security, TODAY.minusDays(30), "10", "100", "-1000", "-2"));
        transactions.save(dividend(portfolio, security, TODAY.minusDays(3), "40", "CT-DIV1"));
        quotes.upsert(PriceQuote.of(security, TODAY.minusDays(3), "110"));

        PortfolioSummaryView summary = adapter.summary(portfolio.value());

        assertThat(summary.portfolioId()).isEqualTo(portfolio.value());
        assertThat(summary.name()).isEqualTo("IBKR");
        assertThat(summary.baseCurrency()).isEqualTo("EUR");
        assertThat(summary.netContributions()).isEqualByComparingTo("1500");
        // Efectivo: 2000 − 500 − 1000 − 2 + 40 = 538.
        assertThat(summary.cashByCurrency()).containsEntry("EUR", new java.math.BigDecimal("538.0000"));
        // Valor total: posición 1100 + efectivo 538.
        assertThat(summary.totalValue()).isEqualByComparingTo("1638");
        assertThat(summary.valuationDate()).isEqualTo(TODAY.minusDays(3));
        assertThat(summary.latentPnl()).isEqualByComparingTo("98");
        assertThat(summary.latentPnlPercent()).isEqualByComparingTo("9.78");
        assertThat(summary.dividendsThisYear()).isEqualByComparingTo("40");
    }

    @Test
    void summary_dividendsOnlyCountTheCurrentYearInGross() {
        PortfolioId portfolio = portfolios.save(Portfolio.create("IBKR", "EUR")).id();
        SecurityId security = persistSecurity("IE00BK5BQT80", "EUR", "VWCE");
        transactions.save(buy(portfolio, security, TODAY.minusYears(2), "10", "100", "-1000", null));
        transactions.save(dividend(portfolio, security, TODAY.minusYears(1).minusDays(5), "30", "CT-OLD"));
        transactions.save(dividend(portfolio, security, LocalDate.of(TODAY.getYear(), 1, 15), "25", "CT-NEW"));
        // La retención sobre la renta es una fila TAX aparte: no resta del bruto.
        transactions.save(InvestmentTransaction.builder()
                .portfolio(portfolio).security(security)
                .type(InvestmentTransactionType.TAX)
                .tradeDate(LocalDate.of(TODAY.getYear(), 1, 15))
                .amount(CurrencyMoney.of("-3.75", "EUR"))
                .externalId("CT-NEWTAX")
                .build());

        assertThat(adapter.summary(portfolio.value()).dividendsThisYear()).isEqualByComparingTo("25");
    }

    @Test
    void summary_convertsFixedAmountsWithTheirOwnSnapshot() {
        PortfolioId portfolio = portfolios.save(Portfolio.create("IBKR", "EUR")).id();
        // Aportación en USD con snapshot RN-7a: 1000 × 0.9 = 900 EUR.
        transactions.save(InvestmentTransaction.builder()
                .portfolio(portfolio)
                .type(InvestmentTransactionType.DEPOSIT)
                .tradeDate(TODAY.minusDays(10))
                .amount(CurrencyMoney.of("1000", "USD"))
                .fxRateToBase("0.9")
                .build());
        // El efectivo USD se valora con la tabla (RN-7b): 1000 × 0.92 = 920 EUR.
        rates.upsert(ExchangeRate.toEur(TODAY.minusDays(1), "USD", "0.92"));

        PortfolioSummaryView summary = adapter.summary(portfolio.value());

        assertThat(summary.netContributions()).isEqualByComparingTo("900");
        assertThat(summary.cashByCurrency()).containsKey("USD");
        assertThat(summary.totalValue()).isEqualByComparingTo("920");
    }

    // ---- valuation history ----

    @Test
    void valuationHistory_hasPointsAtFlowAndQuoteDatesWithSteppedContributions() {
        PortfolioId portfolio = portfolios.save(Portfolio.create("IBKR", "EUR")).id();
        SecurityId security = persistSecurity("IE00BK5BQT80", "EUR", "VWCE");
        LocalDate d1 = TODAY.minusDays(30);
        LocalDate d2 = TODAY.minusDays(20);
        LocalDate d3 = TODAY.minusDays(10);
        transactions.save(deposit(portfolio, d1, "1000"));
        transactions.save(buy(portfolio, security, d2, "8", "100", "-800", null));
        quotes.upsert(PriceQuote.of(security, d2, "100"));
        transactions.save(deposit(portfolio, d3, "500"));
        quotes.upsert(PriceQuote.of(security, d3, "120"));

        var history = adapter.valuationHistory(portfolio.value());

        assertThat(history).extracting(ValuationHistoryView::date).containsExactly(d1, d2, d3);
        assertThat(history).extracting(ValuationHistoryView::contributed)
                .satisfies(list -> {
                    assertThat(list.get(0)).isEqualByComparingTo("1000");
                    assertThat(list.get(1)).isEqualByComparingTo("1000");
                    assertThat(list.get(2)).isEqualByComparingTo("1500");
                });
        // d1: solo efectivo 1000 · d2: 800 en posición + 200 efectivo · d3: 8×120 + 700.
        assertThat(history).extracting(ValuationHistoryView::value)
                .satisfies(list -> {
                    assertThat(list.get(0)).isEqualByComparingTo("1000");
                    assertThat(list.get(1)).isEqualByComparingTo("1000");
                    assertThat(list.get(2)).isEqualByComparingTo("1660");
                });
    }

    // ---- global summary ----

    @Test
    void globalSummary_aggregatesEveryPortfolioInEur() {
        PortfolioId eurPortfolio = portfolios.save(Portfolio.create("IBKR", "EUR")).id();
        PortfolioId usdPortfolio = portfolios.save(Portfolio.create("USA", "USD")).id();
        SecurityId vwce = persistSecurity("IE00BK5BQT80", "EUR", "VWCE");
        SecurityId apple = persistSecurity("US0378331005", "USD", "Apple Inc");
        // Cartera EUR: aportación 1000 gastada entera en la compra → valor = posición 1100.
        transactions.save(deposit(eurPortfolio, TODAY.minusDays(25), "1000"));
        transactions.save(buy(eurPortfolio, vwce, TODAY.minusDays(20), "10", "100", "-1000", null));
        quotes.upsert(PriceQuote.of(vwce, TODAY.minusDays(5), "110")); // 1100 EUR
        // Cartera USD: aportación 500 USD gastada entera → valor = posición 600 USD.
        transactions.save(InvestmentTransaction.builder()
                .portfolio(usdPortfolio)
                .type(InvestmentTransactionType.DEPOSIT)
                .tradeDate(TODAY.minusDays(25))
                .amount(CurrencyMoney.of("500", "USD"))
                .build());
        transactions.save(InvestmentTransaction.builder()
                .portfolio(usdPortfolio).security(apple)
                .type(InvestmentTransactionType.BUY)
                .tradeDate(TODAY.minusDays(20))
                .quantity(Quantity.of("5")).price("100")
                .amount(CurrencyMoney.of("-500", "USD"))
                .build());
        quotes.upsert(PriceQuote.of(apple, TODAY.minusDays(2), "120")); // 600 USD
        rates.upsert(ExchangeRate.toEur(TODAY.minusDays(2), "USD", "0.9")); // → 540 EUR

        InvestmentsSummaryView summary = adapter.globalSummary();

        assertThat(summary.totalValue()).isEqualByComparingTo("1640");
        // Fecha de valoración: la más antigua de las usadas (RF-10).
        assertThat(summary.valuationDate()).isEqualTo(TODAY.minusDays(5));
        assertThat(summary.portfolios()).hasSize(2);
        assertThat(summary.portfolios())
                .anySatisfy(p -> {
                    assertThat(p.name()).isEqualTo("IBKR");
                    assertThat(p.value()).isEqualByComparingTo("1100");
                })
                .anySatisfy(p -> {
                    assertThat(p.name()).isEqualTo("USA");
                    assertThat(p.value()).isEqualByComparingTo("540");
                });
    }

    @Test
    void globalSummary_withoutPortfolios_isEmpty() {
        InvestmentsSummaryView summary = adapter.globalSummary();

        assertThat(summary.totalValue()).isEqualByComparingTo("0");
        assertThat(summary.valuationDate()).isNull();
        assertThat(summary.portfolios()).isEmpty();
    }

    // ---- helpers ----

    // ---- income (H2.2, RF-7) ----

    @Test
    void income_aggregatesGrossAndLinkedWithholdingPerInstrumentAndMonth() {
        PortfolioId portfolio = portfolios.save(Portfolio.create("IBKR", "EUR")).id();
        SecurityId vwce = persistSecurity("IE00BK5BQT80", "EUR", "Vanguard FTSE All-World");
        LocalDate march10 = LocalDate.of(2025, 3, 10);
        transactions.save(dividend(portfolio, vwce, march10, "100", "CT-1"));
        transactions.save(tax(portfolio, vwce, march10, "-15"));
        transactions.save(feeRow(portfolio, march10, "-2"));
        transactions.save(buy(portfolio, vwce, march10, "10", "100", "-1000", "-3"));

        IncomeView view = adapter.income(portfolio.value());

        assertThat(view.portfolioId()).isEqualTo(portfolio.value());
        assertThat(view.baseCurrency()).isEqualTo("EUR");
        assertThat(view.incomes()).hasSize(1);
        var income = view.incomes().getFirst();
        assertThat(income.securityId()).isEqualTo(vwce.value());
        assertThat(income.name()).isEqualTo("Vanguard FTSE All-World");
        assertThat(income.month()).isEqualTo("2025-03");
        assertThat(income.gross()).isEqualByComparingTo("100");
        assertThat(income.withheld()).isEqualByComparingTo("15");
        assertThat(income.net()).isEqualByComparingTo("85");
        assertThat(view.fees()).hasSize(1);
        assertThat(view.fees().getFirst().month()).isEqualTo("2025-03");
        assertThat(view.fees().getFirst().amount()).isEqualByComparingTo("5"); // fila FEE 2 + fee compra 3
        assertThat(view.taxes()).hasSize(1);
        assertThat(view.taxes().getFirst().amount()).isEqualByComparingTo("15");
    }

    @Test
    void income_ordersEntriesByMonthAndInstrumentName() {
        PortfolioId portfolio = portfolios.save(Portfolio.create("IBKR", "EUR")).id();
        SecurityId zeta = persistSecurity("IE00ZZZZZZZ9", "EUR", "Zeta Fund");
        SecurityId alpha = persistSecurity("IE00AAAAAAA1", "EUR", "Alpha Fund");
        transactions.save(dividend(portfolio, zeta, LocalDate.of(2025, 1, 10), "10", "CT-1"));
        transactions.save(dividend(portfolio, alpha, LocalDate.of(2025, 1, 20), "20", "CT-2"));
        transactions.save(dividend(portfolio, alpha, LocalDate.of(2024, 12, 5), "30", "CT-3"));

        IncomeView view = adapter.income(portfolio.value());

        assertThat(view.incomes()).extracting(e -> e.month() + " " + e.name()).containsExactly(
                "2024-12 Alpha Fund", "2025-01 Alpha Fund", "2025-01 Zeta Fund");
    }

    @Test
    void income_unknownPortfolioThrowsNotFound() {
        assertThatThrownBy(() -> adapter.income(999L)).isInstanceOf(NotFoundException.class);
    }

    private SecurityId persistSecurity(String isin, String currency, String name) {
        return securities.save(Security.create(isin, currency, name, null, null, null, null)).id();
    }

    private InvestmentTransaction tax(PortfolioId portfolio, SecurityId security,
                                      LocalDate date, String amount) {
        return InvestmentTransaction.builder()
                .portfolio(portfolio).security(security)
                .type(InvestmentTransactionType.TAX)
                .tradeDate(date)
                .amount(CurrencyMoney.of(amount, "EUR"))
                .build();
    }

    private InvestmentTransaction feeRow(PortfolioId portfolio, LocalDate date, String amount) {
        return InvestmentTransaction.builder()
                .portfolio(portfolio)
                .type(InvestmentTransactionType.FEE)
                .tradeDate(date)
                .amount(CurrencyMoney.of(amount, "EUR"))
                .build();
    }

    private InvestmentTransaction buy(PortfolioId portfolio, SecurityId security, LocalDate date,
                                      String quantity, String price, String amount, String fee) {
        var builder = InvestmentTransaction.builder()
                .portfolio(portfolio).security(security)
                .type(InvestmentTransactionType.BUY)
                .tradeDate(date)
                .quantity(Quantity.of(quantity)).price(price)
                .amount(CurrencyMoney.of(amount, "EUR"));
        if (fee != null) {
            builder.fee(CurrencyMoney.of(fee, "EUR"));
        }
        return builder.build();
    }

    private InvestmentTransaction sell(PortfolioId portfolio, SecurityId security, LocalDate date,
                                       String quantity, String price, String amount) {
        return InvestmentTransaction.builder()
                .portfolio(portfolio).security(security)
                .type(InvestmentTransactionType.SELL)
                .tradeDate(date)
                .quantity(Quantity.of(quantity)).price(price)
                .amount(CurrencyMoney.of(amount, "EUR"))
                .build();
    }

    private InvestmentTransaction deposit(PortfolioId portfolio, LocalDate date, String amount) {
        return InvestmentTransaction.builder()
                .portfolio(portfolio)
                .type(InvestmentTransactionType.DEPOSIT)
                .tradeDate(date)
                .amount(CurrencyMoney.of(amount, "EUR"))
                .build();
    }

    private InvestmentTransaction withdrawal(PortfolioId portfolio, LocalDate date, String amount) {
        return InvestmentTransaction.builder()
                .portfolio(portfolio)
                .type(InvestmentTransactionType.WITHDRAWAL)
                .tradeDate(date)
                .amount(CurrencyMoney.of(amount, "EUR"))
                .build();
    }

    private InvestmentTransaction dividend(PortfolioId portfolio, SecurityId security,
                                           LocalDate date, String amount, String externalId) {
        return InvestmentTransaction.builder()
                .portfolio(portfolio).security(security)
                .type(InvestmentTransactionType.DIVIDEND)
                .tradeDate(date)
                .amount(CurrencyMoney.of(amount, "EUR"))
                .externalId(externalId)
                .build();
    }
}
