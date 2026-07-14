package com.xroig.finance.investments.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link IncomeCalculator} domain service (H2.1, RF-7):
 * dividends/interest aggregated per instrument and month in gross, with the net
 * after subtracting the {@code TAX} withholding linked by instrument (§9) —
 * {@code TRADE_TAX} never enters the income view (it is acquisition cost, RN-3) —
 * plus the fees and withholdings paid per month. Fixed amounts convert to the
 * base currency with each entry's own snapshot (RN-7a), falling back to the rate
 * table (RN-7b) and 1:1 as a last resort.
 */
class IncomeCalculatorTest {

    private static final PortfolioId PORTFOLIO = new PortfolioId(1L);
    private static final SecurityId SECURITY = new SecurityId(10L);
    private static final LocalDate MARCH_10 = LocalDate.of(2025, 3, 10);

    private final IncomeCalculator calculator = new IncomeCalculator();

    private static CurrencyMoney eur(String amount) {
        return CurrencyMoney.of(amount, "EUR");
    }

    private static InvestmentTransaction.Builder tx(InvestmentTransactionType type, LocalDate date) {
        return InvestmentTransaction.builder().portfolio(PORTFOLIO).type(type).tradeDate(date);
    }

    private IncomeStatement calculate(InvestmentTransaction... transactions) {
        return calculator.calculate("EUR", List.of(transactions), new CurrencyConverter(List.of()));
    }

    @Test
    void linkedTaxWithholding_reducesTheInstrumentNet() {
        IncomeStatement result = calculate(
                tx(InvestmentTransactionType.DIVIDEND, MARCH_10)
                        .security(SECURITY).amount(eur("100")).build(),
                tx(InvestmentTransactionType.TAX, MARCH_10)
                        .security(SECURITY).amount(eur("-15")).build());

        InstrumentIncome income = result.incomes().getFirst();
        assertThat(income.gross()).isEqualTo(eur("100"));
        assertThat(income.withheld()).isEqualTo(eur("15"));
        assertThat(income.net()).isEqualTo(eur("85"));
    }

    @Test
    void generalTaxWithoutInstrument_doesNotTouchTheInstrumentNet() {
        IncomeStatement result = calculate(
                tx(InvestmentTransactionType.DIVIDEND, MARCH_10)
                        .security(SECURITY).amount(eur("100")).build(),
                tx(InvestmentTransactionType.TAX, MARCH_10).amount(eur("-9")).build());

        InstrumentIncome income = result.incomes().stream()
                .filter(i -> SECURITY.equals(i.securityId())).findFirst().orElseThrow();
        assertThat(income.net()).isEqualTo(eur("100"));
        assertThat(result.taxesByMonth()).containsEntry(YearMonth.of(2025, 3), eur("9"));
    }

    @Test
    void interestWithoutInstrument_aggregatesUnderANullSecurity() {
        IncomeStatement result = calculate(
                tx(InvestmentTransactionType.INTEREST, MARCH_10).amount(eur("12.50")).build());

        assertThat(result.incomes()).hasSize(1);
        InstrumentIncome income = result.incomes().getFirst();
        assertThat(income.securityId()).isNull();
        assertThat(income.gross()).isEqualTo(eur("12.50"));
        assertThat(income.net()).isEqualTo(eur("12.50"));
    }

    @Test
    void tradeTax_neverEntersTheIncomeView() {
        IncomeStatement result = calculate(
                tx(InvestmentTransactionType.DIVIDEND, MARCH_10)
                        .security(SECURITY).amount(eur("100")).build(),
                tx(InvestmentTransactionType.TRADE_TAX, MARCH_10)
                        .security(SECURITY).amount(eur("-0.50")).build());

        InstrumentIncome income = result.incomes().getFirst();
        assertThat(income.withheld()).isEqualTo(eur("0"));
        assertThat(income.net()).isEqualTo(eur("100"));
        assertThat(result.taxesByMonth()).isEmpty();
    }

    @Test
    void feeRowsAndTradeFeeComponents_aggregateAsFeesPaidPerMonth() {
        IncomeStatement result = calculate(
                tx(InvestmentTransactionType.FEE, MARCH_10).amount(eur("-2")).build(),
                tx(InvestmentTransactionType.BUY, MARCH_10)
                        .security(SECURITY).quantity(Quantity.of("10"))
                        .amount(eur("-1000")).fee(eur("-3")).build());

        assertThat(result.feesByMonth()).containsExactly(
                Map.entry(YearMonth.of(2025, 3), eur("5")));
        assertThat(result.incomes()).isEmpty(); // ni la compra ni la comisión son renta
    }

    @Test
    void ownTaxComponentOfAnIncomeEntry_countsAsWithholding() {
        IncomeStatement result = calculate(
                tx(InvestmentTransactionType.DIVIDEND, MARCH_10)
                        .security(SECURITY).amount(eur("100")).tax(eur("-15")).build());

        InstrumentIncome income = result.incomes().getFirst();
        assertThat(income.withheld()).isEqualTo(eur("15"));
        assertThat(income.net()).isEqualTo(eur("85"));
        assertThat(result.taxesByMonth()).containsEntry(YearMonth.of(2025, 3), eur("15"));
    }

    @Test
    void foreignCurrencyIncome_convertsWithItsOwnSnapshot() {
        IncomeStatement result = calculate(
                tx(InvestmentTransactionType.DIVIDEND, MARCH_10)
                        .security(SECURITY).amount(CurrencyMoney.of("100", "USD"))
                        .fxRateToBase("0.9").build());

        assertThat(result.incomes().getFirst().gross()).isEqualTo(eur("90"));
    }

    @Test
    void foreignCurrencyIncomeWithoutSnapshot_fallsBackToTheRateTable() {
        IncomeStatement result = calculator.calculate("EUR",
                List.of(tx(InvestmentTransactionType.DIVIDEND, MARCH_10)
                        .security(SECURITY).amount(CurrencyMoney.of("100", "USD")).build()),
                new CurrencyConverter(List.of(
                        ExchangeRate.toEur(LocalDate.of(2025, 3, 1), "USD", "0.8"))));

        assertThat(result.incomes().getFirst().gross()).isEqualTo(eur("80"));
    }

    @Test
    void foreignCurrencyIncomeWithoutAnyRate_degradesOneToOne() {
        IncomeStatement result = calculate(
                tx(InvestmentTransactionType.DIVIDEND, MARCH_10)
                        .security(SECURITY).amount(CurrencyMoney.of("100", "USD")).build());

        assertThat(result.incomes().getFirst().gross()).isEqualTo(eur("100"));
    }

    @Test
    void dividend_aggregatesAsGrossIncomeOfItsInstrumentAndMonth() {
        IncomeStatement result = calculate(
                tx(InvestmentTransactionType.DIVIDEND, MARCH_10)
                        .security(SECURITY).amount(eur("100")).build());

        assertThat(result.incomes()).hasSize(1);
        InstrumentIncome income = result.incomes().getFirst();
        assertThat(income.securityId()).isEqualTo(SECURITY);
        assertThat(income.month()).isEqualTo(YearMonth.of(2025, 3));
        assertThat(income.gross()).isEqualTo(eur("100"));
        assertThat(income.withheld()).isEqualTo(eur("0"));
        assertThat(income.net()).isEqualTo(eur("100"));
    }
}
