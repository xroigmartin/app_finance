package com.xroig.finance.investments.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link PositionCalculator} domain service (H1.4): positions
 * as direct sums of signed quantities (RN-3), average cost with the capitalized
 * purchase cost (|amount| + |fee| + |trade_tax|, RN-3), realized P&L against the
 * average capitalized cost, splits as quantity deltas at zero cost, portfolio cash
 * per currency as direct sums of signed amounts (RN-2), cost conversion to the
 * base currency with each entry's own snapshot (RN-7a) falling back to the rate
 * table (RN-7b), and the tolerant sell-without-position detection (RN-4) — the
 * calculator flags, the use case decides hard-400 vs warning.
 */
class PositionCalculatorTest {

    private static final PortfolioId PORTFOLIO = new PortfolioId(1L);
    private static final SecurityId SECURITY = new SecurityId(10L);
    private static final LocalDate D1 = LocalDate.of(2025, 3, 3);
    private static final LocalDate D2 = LocalDate.of(2025, 6, 2);

    private final PositionCalculator calculator = new PositionCalculator();

    private static CurrencyMoney eur(String amount) {
        return CurrencyMoney.of(amount, "EUR");
    }

    private static InvestmentTransaction.Builder tx(InvestmentTransactionType type, LocalDate date) {
        return InvestmentTransaction.builder().portfolio(PORTFOLIO).type(type).tradeDate(date);
    }

    private static InvestmentTransaction buyEur(LocalDate date, String qty, String amount, String fee) {
        return tx(InvestmentTransactionType.BUY, date)
                .security(SECURITY).quantity(Quantity.of(qty)).amount(eur(amount))
                .fee(fee == null ? null : eur(fee))
                .build();
    }

    private static InvestmentTransaction sellEur(LocalDate date, String qty, String amount, String fee) {
        return tx(InvestmentTransactionType.SELL, date)
                .security(SECURITY).quantity(Quantity.of(qty)).amount(eur(amount))
                .fee(fee == null ? null : eur(fee))
                .build();
    }

    private PortfolioPositions calculate(InvestmentTransaction... transactions) {
        return calculator.calculate("EUR", List.of(transactions), new CurrencyConverter(List.of()));
    }

    private static Position position(PortfolioPositions result) {
        return result.positions().stream()
                .filter(p -> p.securityId().equals(SECURITY))
                .findFirst().orElseThrow();
    }

    @Test
    void buysAccumulateTheCapitalizedAverageCost() {
        PortfolioPositions result = calculate(
                buyEur(D1, "10", "-1000", "-5"),
                buyEur(D2, "10", "-1100", "-5"));

        Position position = position(result);
        assertThat(position.quantity()).isEqualTo(Quantity.of("20"));
        assertThat(position.costBasis()).isEqualTo(eur("2110")); // 1005 + 1105 capitalizados
        assertThat(position.averageCost()).isEqualTo(eur("105.50"));
        assertThat(position.realizedPnl()).isEqualTo(eur("0"));
    }

    @Test
    void tradeTaxOnTheBuyDateCapitalizesIntoTheCost() {
        PortfolioPositions result = calculate(
                buyEur(D1, "10", "-1000", "-5"),
                tx(InvestmentTransactionType.TRADE_TAX, D1)
                        .security(SECURITY).amount(eur("-3")).build());

        assertThat(position(result).costBasis()).isEqualTo(eur("1008"));
    }

    @Test
    void sellRealizesPnlAgainstTheAverageCapitalizedCost() {
        PortfolioPositions result = calculate(
                buyEur(D1, "10", "-1000", "-5"),
                buyEur(D1, "10", "-1100", "-5"),
                sellEur(D2, "-5", "600", "-2"));

        Position position = position(result);
        // Neto percibido 598 − coste promedio capitalizado de lo vendido (105.5 × 5 = 527.5).
        assertThat(position.realizedPnl()).isEqualTo(eur("70.50"));
        assertThat(position.quantity()).isEqualTo(Quantity.of("15"));
        assertThat(position.costBasis()).isEqualTo(eur("1582.50"));
        assertThat(position.averageCost()).isEqualTo(eur("105.50")); // la venta no cambia el coste medio
    }

    @Test
    void tradeTaxOnTheSellDateReducesTheNetProceeds() {
        PortfolioPositions result = calculate(
                buyEur(D1, "10", "-1000", null),
                sellEur(D2, "-5", "600", "-2"),
                tx(InvestmentTransactionType.TRADE_TAX, D2)
                        .security(SECURITY).amount(eur("-3")).build());

        // Neto 595 − coste de lo vendido 500 = 95.
        assertThat(position(result).realizedPnl()).isEqualTo(eur("95"));
    }

    @Test
    void splitIsAQuantityDeltaAtZeroCost() {
        PortfolioPositions result = calculate(
                buyEur(D1, "2", "-200", null),
                tx(InvestmentTransactionType.SPLIT, D2)
                        .security(SECURITY).quantity(Quantity.of("18")).amount(eur("0")).build());

        Position position = position(result);
        assertThat(position.quantity()).isEqualTo(Quantity.of("20"));
        assertThat(position.costBasis()).isEqualTo(eur("200")); // mismo coste total…
        assertThat(position.averageCost()).isEqualTo(eur("10")); // …repartido entre más títulos
    }

    @Test
    void sellWithoutEnoughPositionIsFlaggedButProcessed() {
        PortfolioPositions result = calculate(
                buyEur(D1, "3", "-300", null),
                sellEur(D2, "-5", "500", null));

        assertThat(result.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.securityId()).isEqualTo(SECURITY);
            assertThat(warning.tradeDate()).isEqualTo(D2);
        });
        // La fila se procesa igual: la posición queda negativa (falta histórico anterior, RN-4).
        Position position = position(result);
        assertThat(position.quantity()).isEqualTo(Quantity.of("-2"));
        // P&L realizado sobre la parte cubierta: 500 − 300 = 200.
        assertThat(position.realizedPnl()).isEqualTo(eur("200"));
        assertThat(position.costBasis()).isEqualTo(eur("0"));
    }

    @Test
    void precisionResiduesDoNotFlagTheClosingSale() {
        PortfolioPositions result = calculate(
                buyEur(D1, "3.33333333", "-100", null),
                buyEur(D1, "3.33333333", "-100", null),
                buyEur(D1, "3.33333333", "-100", null),
                // Posición 9.99999999: cerrar con 10 deja un residuo de 1e-8 que no es warning (RN-4).
                sellEur(D2, "-10", "310", null));

        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void acquisitionsProcessBeforeDisposalsWithinTheSameDate() {
        // La venta llega antes en el input, pero compra y venta son del mismo día.
        PortfolioPositions result = calculate(
                sellEur(D1, "-10", "1100", null),
                buyEur(D1, "10", "-1000", null));

        assertThat(result.warnings()).isEmpty();
        Position position = position(result);
        assertThat(position.quantity()).isEqualTo(Quantity.ZERO);
        assertThat(position.realizedPnl()).isEqualTo(eur("100"));
    }

    @Test
    void cashIsADirectSumPerCurrencyIncludingFeeTaxAndCounterLegs() {
        PortfolioPositions result = calculate(
                tx(InvestmentTransactionType.DEPOSIT, D1).amount(eur("1000")).build(),
                tx(InvestmentTransactionType.FX_TRADE, D1)
                        .amount(eur("-500"))
                        .counterAmount(CurrencyMoney.of("540", "USD"))
                        .fee(eur("-2")).build(),
                tx(InvestmentTransactionType.BUY, D2)
                        .security(SECURITY).quantity(Quantity.of("2"))
                        .amount(CurrencyMoney.of("-400", "USD")).build(),
                tx(InvestmentTransactionType.DIVIDEND, D2)
                        .security(SECURITY).amount(CurrencyMoney.of("10", "USD")).build(),
                tx(InvestmentTransactionType.TAX, D2)
                        .security(SECURITY).amount(CurrencyMoney.of("-1.5", "USD")).build());

        assertThat(result.cashByCurrency())
                .containsEntry("EUR", eur("498"))        // 1000 − 500 − 2
                .containsEntry("USD", CurrencyMoney.of("148.50", "USD")); // 540 − 400 + 10 − 1.5
    }

    @Test
    void convertsEachCostComponentWithTheEntrySnapshot() {
        // Cartera base EUR, compra en USD con snapshot fxRateToBase = 0.9 (RN-7a).
        PortfolioPositions result = calculate(
                tx(InvestmentTransactionType.BUY, D1)
                        .security(SECURITY).quantity(Quantity.of("10"))
                        .amount(CurrencyMoney.of("-1000", "USD"))
                        .fee(CurrencyMoney.of("-10", "USD"))
                        .fxRateToBase("0.9")
                        .build());

        Position position = position(result);
        assertThat(position.costBasis()).isEqualTo(eur("909")); // (1000 + 10) × 0.9
        assertThat(position.costBasis().currency()).isEqualTo("EUR");
    }

    @Test
    void fallsBackToTheRateTableWhenTheEntryHasNoSnapshot() {
        // Apunte manual sin snapshot: convierte con el último tipo ≤ fecha (RN-7b).
        CurrencyConverter rates = new CurrencyConverter(List.of(
                ExchangeRate.toEur(D1.minusDays(3), "USD", "0.8")));
        PortfolioPositions result = calculator.calculate("EUR", List.of(
                tx(InvestmentTransactionType.BUY, D1)
                        .security(SECURITY).quantity(Quantity.of("10"))
                        .amount(CurrencyMoney.of("-1000", "USD"))
                        .build()),
                rates);

        assertThat(position(result).costBasis()).isEqualTo(eur("800"));
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void warnsWhenNoConversionIsAvailableAndKeepsTheRawAmount() {
        PortfolioPositions result = calculate(
                tx(InvestmentTransactionType.BUY, D1)
                        .security(SECURITY).quantity(Quantity.of("10"))
                        .amount(CurrencyMoney.of("-1000", "USD"))
                        .build());

        // Sin snapshot ni tabla: 1:1 visible + warning (no rompe el cálculo).
        assertThat(position(result).costBasis()).isEqualTo(eur("1000"));
        assertThat(result.warnings()).singleElement().satisfies(warning ->
                assertThat(warning.message()).contains("tipo de cambio"));
    }

    @Test
    void realizedByYearBucketsBySaleYear() {
        PortfolioPositions result = calculate(
                buyEur(D1, "10", "-1000", null),
                sellEur(LocalDate.of(2025, 4, 1), "-4", "500", null),
                sellEur(LocalDate.of(2026, 2, 1), "-3", "400", null));

        Position position = position(result);
        // 2025: neto 500 − coste 400 (100×4) = 100. 2026: neto 400 − coste 300 (100×3) = 100.
        assertThat(position.realizedByYear())
                .containsEntry(2025, eur("100"))
                .containsEntry(2026, eur("100"));
    }

    @Test
    void realizedByYearIsEmptyWhenNeverSold() {
        PortfolioPositions result = calculate(buyEur(D1, "10", "-1000", null));

        assertThat(position(result).realizedByYear()).isEmpty();
    }

    @Test
    void realizedByYearSumsUpToTheRunningTotal() {
        PortfolioPositions result = calculate(
                buyEur(D1, "10", "-1000", null),
                sellEur(LocalDate.of(2025, 4, 1), "-4", "500", null),
                sellEur(LocalDate.of(2026, 2, 1), "-3", "400", null));

        Position position = position(result);
        CurrencyMoney sumOfYears = position.realizedByYear().values().stream()
                .reduce(eur("0"), CurrencyMoney::add);
        assertThat(sumOfYears).isEqualTo(position.realizedPnl());
    }

    @Test
    void averageCostIsUndefinedWithoutPositiveQuantity() {
        PortfolioPositions result = calculate(
                buyEur(D1, "10", "-1000", null),
                sellEur(D2, "-10", "1100", null));

        Position position = position(result);
        assertThat(position.quantity()).isEqualTo(Quantity.ZERO);
        assertThat(position.averageCost()).isNull();
        assertThat(position.realizedPnl()).isEqualTo(eur("100"));
    }
}
