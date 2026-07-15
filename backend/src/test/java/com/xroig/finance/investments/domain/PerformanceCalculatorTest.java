package com.xroig.finance.investments.domain;

import com.xroig.finance.investments.domain.PerformanceCalculator.Cashflow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link PerformanceCalculator} domain service (H3.1, RN-8):
 * XIRR as the annualized IRR of dated cashflows — known cases, convergence from
 * Newton-Raphson with the bisection fallback, and the degenerate inputs that
 * yield no result (fewer than two flows, all the same sign, all on one date).
 * The portfolio variant builds the flows itself: external flows only
 * ({@code DEPOSIT}/{@code WITHDRAWAL}, investor sign) plus the current value —
 * {@code FX_TRADE} is not an external flow.
 */
class PerformanceCalculatorTest {

    private static final LocalDate D0 = LocalDate.of(2024, 1, 1);

    private final PerformanceCalculator calculator = new PerformanceCalculator();

    private static Cashflow flow(LocalDate date, String amount) {
        return new Cashflow(date, new BigDecimal(amount));
    }

    private static org.assertj.core.data.Offset<BigDecimal> tolerance() {
        return org.assertj.core.data.Offset.offset(new BigDecimal("0.000001"));
    }

    @Test
    void xirr_multiFlowWithWithdrawal_solvesTheKnownRate() {
        // −1000 + 500/(1+r) + 660/(1+r)² = 0 → r = 10 % exacto.
        Optional<BigDecimal> rate = calculator.xirr(List.of(
                flow(D0, "-1000"),
                flow(D0.plusDays(365), "500"),
                flow(D0.plusDays(730), "660")));

        assertThat(rate).isPresent();
        assertThat(rate.get()).isCloseTo(new BigDecimal("0.10"), tolerance());
    }

    @Test
    void xirr_lossYieldsANegativeRate() {
        Optional<BigDecimal> rate = calculator.xirr(List.of(
                flow(D0, "-1000"),
                flow(D0.plusDays(365), "900")));

        assertThat(rate).isPresent();
        assertThat(rate.get()).isCloseTo(new BigDecimal("-0.10"), tolerance());
    }

    @Test
    void xirr_extremeGainConverges() {
        // −1000 → 50000 en un año: r = 49 (4900 %), lejos del arranque de Newton.
        Optional<BigDecimal> rate = calculator.xirr(List.of(
                flow(D0, "-1000"),
                flow(D0.plusDays(365), "50000")));

        assertThat(rate).isPresent();
        assertThat(rate.get()).isCloseTo(new BigDecimal("49"), tolerance());
    }

    @Test
    void xirr_nearTotalLossConverges() {
        // −1000 → 50 en un año: r = −0.95, pegado al límite del dominio (r > −1).
        Optional<BigDecimal> rate = calculator.xirr(List.of(
                flow(D0, "-1000"),
                flow(D0.plusDays(365), "50")));

        assertThat(rate).isPresent();
        assertThat(rate.get()).isCloseTo(new BigDecimal("-0.95"), tolerance());
    }

    @Test
    void xirr_solutionZeroesTheNpvOnAnIrregularSeries() {
        // Serie sin solución cerrada: se verifica la propiedad NPV(r) ≈ 0.
        List<Cashflow> flows = List.of(
                flow(D0, "-2500"),
                flow(D0.plusDays(140), "-1200"),
                flow(D0.plusDays(400), "300"),
                flow(D0.plusDays(730), "4321.99"));

        Optional<BigDecimal> rate = calculator.xirr(flows);

        assertThat(rate).isPresent();
        double r = rate.get().doubleValue();
        double npv = flows.stream()
                .mapToDouble(f -> f.amount().doubleValue()
                        / Math.pow(1 + r, java.time.temporal.ChronoUnit.DAYS.between(D0, f.date()) / 365.0))
                .sum();
        // La tasa se devuelve redondeada a escala 6: el NPV residual queda por debajo del céntimo.
        assertThat(Math.abs(npv)).isLessThan(1e-2);
    }

    @Test
    void xirr_degenerateInputsYieldEmpty() {
        assertThat(calculator.xirr(List.of())).isEmpty();
        assertThat(calculator.xirr(List.of(flow(D0, "-1000")))).isEmpty();
        // Todos los flujos del mismo signo: no hay TIR.
        assertThat(calculator.xirr(List.of(flow(D0, "-1000"), flow(D0.plusDays(365), "-500")))).isEmpty();
        // Todos los flujos el mismo día: no hay tiempo transcurrido.
        assertThat(calculator.xirr(List.of(flow(D0, "-1000"), flow(D0, "1100")))).isEmpty();
        // Los flujos a cero no cuentan como flujo.
        assertThat(calculator.xirr(List.of(flow(D0, "-1000"), flow(D0.plusDays(365), "0")))).isEmpty();
    }

    // ---- portfolioTwr: subperiodos delimitados por flujos externos (RN-8) ----

    private static PerformanceCalculator.ValuationPoint valuation(LocalDate date, String value) {
        return new PerformanceCalculator.ValuationPoint(date, new BigDecimal(value));
    }

    @Test
    void twr_withoutIntermediateFlowsIsThePlainReturn() {
        List<InvestmentTransaction> transactions = List.of(
                tx(InvestmentTransactionType.DEPOSIT, D0)
                        .amount(CurrencyMoney.of("1000", "EUR")).build());

        Optional<BigDecimal> twr = calculator.portfolioTwr("EUR", transactions,
                new CurrencyConverter(List.of()),
                List.of(valuation(D0, "1000"), valuation(D0.plusDays(365), "1100")));

        assertThat(twr).isPresent();
        assertThat(twr.get()).isCloseTo(new BigDecimal("0.10"), tolerance());
    }

    @Test
    void twr_neutralizesIntermediateContributions() {
        // 1000 → 1100 (+10 %), aportación de 500 (V=1600 incluye el flujo), 1600 → 1760 (+10 %):
        // TWR = 1.1 × 1.1 − 1 = 21 %, independiente del tamaño del flujo.
        List<InvestmentTransaction> transactions = List.of(
                tx(InvestmentTransactionType.DEPOSIT, D0)
                        .amount(CurrencyMoney.of("1000", "EUR")).build(),
                tx(InvestmentTransactionType.DEPOSIT, D0.plusDays(180))
                        .amount(CurrencyMoney.of("500", "EUR")).build());

        Optional<BigDecimal> twr = calculator.portfolioTwr("EUR", transactions,
                new CurrencyConverter(List.of()),
                List.of(valuation(D0, "1000"),
                        valuation(D0.plusDays(180), "1600"),
                        valuation(D0.plusDays(365), "1760")));

        assertThat(twr).isPresent();
        assertThat(twr.get()).isCloseTo(new BigDecimal("0.21"), tolerance());
    }

    @Test
    void twr_neutralizesIntermediateWithdrawals() {
        // 1000 → 1100 (+10 %), retirada de 550 (V=550 incluye el flujo), 550 → 605 (+10 %): TWR = 21 %.
        List<InvestmentTransaction> transactions = List.of(
                tx(InvestmentTransactionType.DEPOSIT, D0)
                        .amount(CurrencyMoney.of("1000", "EUR")).build(),
                tx(InvestmentTransactionType.WITHDRAWAL, D0.plusDays(180))
                        .amount(CurrencyMoney.of("-550", "EUR")).build());

        Optional<BigDecimal> twr = calculator.portfolioTwr("EUR", transactions,
                new CurrencyConverter(List.of()),
                List.of(valuation(D0, "1000"),
                        valuation(D0.plusDays(180), "550"),
                        valuation(D0.plusDays(365), "605")));

        assertThat(twr).isPresent();
        assertThat(twr.get()).isCloseTo(new BigDecimal("0.21"), tolerance());
    }

    @Test
    void twr_lossIsNegative() {
        Optional<BigDecimal> twr = calculator.portfolioTwr("EUR",
                List.of(tx(InvestmentTransactionType.DEPOSIT, D0)
                        .amount(CurrencyMoney.of("1000", "EUR")).build()),
                new CurrencyConverter(List.of()),
                List.of(valuation(D0, "1000"), valuation(D0.plusDays(365), "900")));

        assertThat(twr).isPresent();
        assertThat(twr.get()).isCloseTo(new BigDecimal("-0.10"), tolerance());
    }

    @Test
    void twr_fxTradeNeitherDelimitsNorCounts() {
        // Un FX_TRADE en una fecha con punto de valoración no es flujo externo (RN-8):
        // el subperiodo se calcula sin restarle nada y el TWR es el rendimiento puro.
        List<InvestmentTransaction> transactions = List.of(
                tx(InvestmentTransactionType.DEPOSIT, D0)
                        .amount(CurrencyMoney.of("1000", "EUR")).build(),
                tx(InvestmentTransactionType.FX_TRADE, D0.plusDays(180))
                        .amount(CurrencyMoney.of("-500", "EUR"))
                        .counterAmount(CurrencyMoney.of("540", "USD")).build());

        Optional<BigDecimal> twr = calculator.portfolioTwr("EUR", transactions,
                new CurrencyConverter(List.of()),
                List.of(valuation(D0, "1000"),
                        valuation(D0.plusDays(180), "1100"),
                        valuation(D0.plusDays(365), "1210")));

        assertThat(twr).isPresent();
        assertThat(twr.get()).isCloseTo(new BigDecimal("0.21"), tolerance());
    }

    @Test
    void twr_convertsForeignFlowsWithTheirSnapshot() {
        // Aportación intermedia de 500 USD con snapshot 0.9 → flujo de 450 EUR (RN-7a).
        List<InvestmentTransaction> transactions = List.of(
                tx(InvestmentTransactionType.DEPOSIT, D0)
                        .amount(CurrencyMoney.of("1000", "EUR")).build(),
                tx(InvestmentTransactionType.DEPOSIT, D0.plusDays(180))
                        .amount(CurrencyMoney.of("500", "USD")).fxRateToBase("0.9").build());

        Optional<BigDecimal> twr = calculator.portfolioTwr("EUR", transactions,
                new CurrencyConverter(List.of()),
                List.of(valuation(D0, "1000"),
                        valuation(D0.plusDays(180), "1550"),   // 1100 + 450 del flujo
                        valuation(D0.plusDays(365), "1705"))); // +10 %

        assertThat(twr).isPresent();
        assertThat(twr.get()).isCloseTo(new BigDecimal("0.21"), tolerance());
    }

    @Test
    void twr_degenerateInputsYieldEmpty() {
        CurrencyConverter rates = new CurrencyConverter(List.of());
        // Menos de dos puntos de valoración.
        assertThat(calculator.portfolioTwr("EUR", List.of(), rates,
                List.of(valuation(D0, "1000")))).isEmpty();
        assertThat(calculator.portfolioTwr("EUR", List.of(), rates, List.of())).isEmpty();
        // Valor de arranque de un subperiodo no positivo.
        assertThat(calculator.portfolioTwr("EUR", List.of(), rates,
                List.of(valuation(D0, "0"), valuation(D0.plusDays(365), "1100")))).isEmpty();
    }

    // ---- portfolioXirr: flujos externos + valor actual (RN-8) ----

    private static final PortfolioId PORTFOLIO = new PortfolioId(1L);

    private static InvestmentTransaction.Builder tx(InvestmentTransactionType type, LocalDate date) {
        return InvestmentTransaction.builder().portfolio(PORTFOLIO).type(type).tradeDate(date);
    }

    @Test
    void portfolioXirr_usesExternalFlowsPlusCurrentValue() {
        List<InvestmentTransaction> transactions = List.of(
                tx(InvestmentTransactionType.DEPOSIT, D0)
                        .amount(CurrencyMoney.of("1000", "EUR")).build());

        Optional<BigDecimal> rate = calculator.portfolioXirr("EUR", transactions,
                new CurrencyConverter(List.of()),
                CurrencyMoney.of("1100", "EUR"), D0.plusDays(365));

        assertThat(rate).isPresent();
        assertThat(rate.get()).isCloseTo(new BigDecimal("0.10"), tolerance());
    }

    @Test
    void portfolioXirr_ignoresFxTradesAndInternalOperations() {
        // Un FX_TRADE y una compra por medio no son flujos externos (RN-8):
        // el resultado es el mismo 10 % que con solo la aportación y el valor.
        List<InvestmentTransaction> transactions = List.of(
                tx(InvestmentTransactionType.DEPOSIT, D0)
                        .amount(CurrencyMoney.of("1000", "EUR")).build(),
                tx(InvestmentTransactionType.FX_TRADE, D0.plusDays(100))
                        .amount(CurrencyMoney.of("-500", "EUR"))
                        .counterAmount(CurrencyMoney.of("540", "USD")).build(),
                tx(InvestmentTransactionType.BUY, D0.plusDays(120))
                        .security(new SecurityId(10L)).quantity(Quantity.of("4"))
                        .amount(CurrencyMoney.of("-400", "EUR")).build());

        Optional<BigDecimal> rate = calculator.portfolioXirr("EUR", transactions,
                new CurrencyConverter(List.of()),
                CurrencyMoney.of("1100", "EUR"), D0.plusDays(365));

        assertThat(rate).isPresent();
        assertThat(rate.get()).isCloseTo(new BigDecimal("0.10"), tolerance());
    }

    @Test
    void portfolioXirr_withdrawalCountsAsPositiveInvestorFlow() {
        List<InvestmentTransaction> transactions = List.of(
                tx(InvestmentTransactionType.DEPOSIT, D0)
                        .amount(CurrencyMoney.of("1000", "EUR")).build(),
                tx(InvestmentTransactionType.WITHDRAWAL, D0.plusDays(365))
                        .amount(CurrencyMoney.of("-500", "EUR")).build());

        Optional<BigDecimal> rate = calculator.portfolioXirr("EUR", transactions,
                new CurrencyConverter(List.of()),
                CurrencyMoney.of("660", "EUR"), D0.plusDays(730));

        assertThat(rate).isPresent();
        assertThat(rate.get()).isCloseTo(new BigDecimal("0.10"), tolerance());
    }

    @Test
    void portfolioXirr_convertsForeignFlowsWithTheirSnapshot() {
        // Aportación de 1000 USD con snapshot 0.9 → flujo inversor de −900 EUR (RN-7a).
        List<InvestmentTransaction> transactions = List.of(
                tx(InvestmentTransactionType.DEPOSIT, D0)
                        .amount(CurrencyMoney.of("1000", "USD")).fxRateToBase("0.9").build());

        Optional<BigDecimal> rate = calculator.portfolioXirr("EUR", transactions,
                new CurrencyConverter(List.of()),
                CurrencyMoney.of("990", "EUR"), D0.plusDays(365));

        assertThat(rate).isPresent();
        assertThat(rate.get()).isCloseTo(new BigDecimal("0.10"), tolerance());
    }

    @Test
    void xirr_oneYearGainOfTenPercent() {
        Optional<BigDecimal> rate = calculator.xirr(List.of(
                flow(D0, "-1000"),
                flow(D0.plusDays(365), "1100")));

        assertThat(rate).isPresent();
        assertThat(rate.get()).isCloseTo(new BigDecimal("0.10"), org.assertj.core.data.Offset.offset(new BigDecimal("0.000001")));
    }
}
