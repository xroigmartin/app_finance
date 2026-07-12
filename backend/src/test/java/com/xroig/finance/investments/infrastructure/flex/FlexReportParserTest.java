package com.xroig.finance.investments.infrastructure.flex;

import com.xroig.finance.investments.application.FlexReport;
import com.xroig.finance.investments.application.FlexRow;
import com.xroig.finance.investments.domain.ExchangeRate;
import com.xroig.finance.investments.domain.InvestmentTransactionType;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests of the {@link FlexReportParser} ACL against a trimmed, anonymized
 * fixture mirroring the real IBKR Activity Flex reports (§9, validated Flex
 * configuration): section/level filtering (Trades→ORDER, Cash→DETAIL, Corporate
 * Actions→DETAIL, FTT→ORDER_SUMMARY), the sign-based FX-trade leg translation,
 * origin-prefixed external ids (RN-10), open positions → quotes at the
 * statement's toDate, conversion rates filtered to the report's currencies, and
 * per-row errors that never abort the rest (§8).
 */
class FlexReportParserTest {

    private static FlexReport report;

    @BeforeAll
    static void parseFixture() throws IOException {
        byte[] xml = FlexReportParserTest.class
                .getResourceAsStream("/investments/flex/flex-sample.xml").readAllBytes();
        report = new FlexReportParser().read(new MockMultipartFile("file", "flex.xml", "text/xml", xml));
    }

    private static FlexRow row(String externalId) {
        return report.rows().stream()
                .filter(r -> externalId.equals(r.externalId()))
                .findFirst().orElseThrow();
    }

    @Test
    void accountInformation_exposesTheAccountAndItsBaseCurrency() {
        assertThat(report.accountId()).isEqualTo("U1234567");
        assertThat(report.baseCurrency()).isEqualTo("EUR");
        assertThat(report.fromDate()).isEqualTo(LocalDate.of(2024, 1, 5));
        assertThat(report.toDate()).isEqualTo(LocalDate.of(2024, 12, 31));
    }

    @Test
    void stockOrder_buy_mapsQuantityProceedsCommissionAndInstrument() {
        FlexRow buy = row("ORD-643660025");

        assertThat(buy.type()).isEqualTo(InvestmentTransactionType.BUY);
        assertThat(buy.tradeDate()).isEqualTo(LocalDate.of(2024, 7, 2));
        assertThat(buy.quantity()).isEqualByComparingTo("2");
        assertThat(buy.price()).isEqualByComparingTo("131.8");
        assertThat(buy.amount()).isEqualByComparingTo("-263.6");
        assertThat(buy.currency()).isEqualTo("EUR");
        assertThat(buy.fee()).isEqualByComparingTo("-3");
        assertThat(buy.feeCurrency()).isEqualTo("EUR");
        assertThat(buy.fxRateToBase()).isEqualByComparingTo("1");
        assertThat(buy.instrument().isin()).isEqualTo("NL0000235190");
        assertThat(buy.instrument().currency()).isEqualTo("EUR");
        assertThat(buy.instrument().name()).isEqualTo("AIRBUS SE");
        assertThat(buy.instrument().ticker()).isEqualTo("AIRd");
        assertThat(buy.instrument().exchange()).isEqualTo("SBF");
        assertThat(buy.instrument().figi()).isEqualTo("BBG000CSHLR0");
    }

    @Test
    void stockOrder_sell_keepsTheFlexSigns() {
        FlexRow sell = row("ORD-664635087");

        assertThat(sell.type()).isEqualTo(InvestmentTransactionType.SELL);
        assertThat(sell.quantity()).isEqualByComparingTo("-2");
        assertThat(sell.amount()).isEqualByComparingTo("269");
    }

    @Test
    void fxOrder_sellOfThePair_outgoingLegIsTheQuantityInTheSymbolBaseCurrency() {
        FlexRow fx = row("ORD-913136634");

        assertThat(fx.type()).isEqualTo(InvestmentTransactionType.FX_TRADE);
        assertThat(fx.instrument()).isNull();
        assertThat(fx.amount()).isEqualByComparingTo("-51.99");
        assertThat(fx.currency()).isEqualTo("EUR");
        assertThat(fx.counterAmount()).isEqualByComparingTo("44.9947455");
        assertThat(fx.counterCurrency()).isEqualTo("GBP");
        assertThat(fx.fee()).isEqualByComparingTo("-1.71794");
        assertThat(fx.feeCurrency()).isEqualTo("EUR");
        // El snapshot del Flex apunta a la divisa del apunte original (GBP), no a la
        // de la pierna saliente (EUR): no aplica.
        assertThat(fx.fxRateToBase()).isNull();
    }

    @Test
    void fxOrder_buyOfThePair_outgoingLegIsTheProceedsWithItsSnapshot() {
        FlexRow fx = row("ORD-700000001");

        assertThat(fx.type()).isEqualTo(InvestmentTransactionType.FX_TRADE);
        assertThat(fx.amount()).isEqualByComparingTo("-108.23");
        assertThat(fx.currency()).isEqualTo("USD");
        assertThat(fx.counterAmount()).isEqualByComparingTo("100");
        assertThat(fx.counterCurrency()).isEqualTo("EUR");
        assertThat(fx.fxRateToBase()).isEqualByComparingTo("0.92");
    }

    @Test
    void cashTransactions_mapDividendTaxDepositWithdrawalAndInterest() {
        FlexRow dividend = row("CT-2706035381");
        assertThat(dividend.type()).isEqualTo(InvestmentTransactionType.DIVIDEND);
        assertThat(dividend.amount()).isEqualByComparingTo("1.75");
        assertThat(dividend.tradeDate()).isEqualTo(LocalDate.of(2024, 5, 7));
        assertThat(dividend.instrument().isin()).isEqualTo("NL0010273215");

        FlexRow withholding = row("CT-2706035382");
        assertThat(withholding.type()).isEqualTo(InvestmentTransactionType.TAX);
        assertThat(withholding.amount()).isEqualByComparingTo("-0.26");
        assertThat(withholding.tradeDate()).isEqualTo(dividend.tradeDate());
        assertThat(withholding.instrument().isin()).isEqualTo("NL0010273215");

        FlexRow deposit = row("CT-2392643511");
        assertThat(deposit.type()).isEqualTo(InvestmentTransactionType.DEPOSIT);
        assertThat(deposit.amount()).isEqualByComparingTo("1000");
        assertThat(deposit.tradeDate()).isEqualTo(LocalDate.of(2024, 1, 5));
        assertThat(deposit.instrument()).isNull();

        FlexRow withdrawal = row("CT-2392643999");
        assertThat(withdrawal.type()).isEqualTo(InvestmentTransactionType.WITHDRAWAL);
        assertThat(withdrawal.amount()).isEqualByComparingTo("-250");

        FlexRow interest = row("CT-2400000001");
        assertThat(interest.type()).isEqualTo(InvestmentTransactionType.INTEREST);
        assertThat(interest.amount()).isEqualByComparingTo("1.23");
    }

    @Test
    void corporateAction_split_isAQuantityDeltaAtZeroCostOnTheActionDate() {
        FlexRow split = row("CA-2794194316");

        assertThat(split.type()).isEqualTo(InvestmentTransactionType.SPLIT);
        assertThat(split.quantity()).isEqualByComparingTo("18");
        assertThat(split.amount()).isEqualByComparingTo("0");
        assertThat(split.currency()).isEqualTo("USD");
        assertThat(split.tradeDate()).isEqualTo(LocalDate.of(2024, 6, 7));
        assertThat(split.instrument().isin()).isEqualTo("US67066G1040");
    }

    @Test
    void transactionTax_orderSummary_becomesATradeTaxRowLinkedByInstrumentAndDate() {
        FlexRow ftt = row("FTT-2858322004");

        assertThat(ftt.type()).isEqualTo(InvestmentTransactionType.TRADE_TAX);
        assertThat(ftt.amount()).isEqualByComparingTo("-0.41");
        assertThat(ftt.currency()).isEqualTo("EUR");
        assertThat(ftt.tradeDate()).isEqualTo(LocalDate.of(2024, 7, 2));
        assertThat(ftt.instrument().isin()).isEqualTo("FR0013447729");
        // El TransactionTaxDetail (ORDER_DETAIL) duplica la misma tasa y se ignora.
        assertThat(report.rows().stream()
                .filter(r -> r.type() == InvestmentTransactionType.TRADE_TAX)).hasSize(1);
    }

    @Test
    void summaryLevels_areIgnoredWithoutGeneratingRowsOrErrors() {
        // SYMBOL_SUMMARY de Trades y SUMMARY de Corporate Actions (accountId="-").
        assertThat(report.rows()).hasSize(11);
        assertThat(report.rows().stream()
                .filter(r -> r.type() == InvestmentTransactionType.SPLIT)).hasSize(1);
    }

    @Test
    void openPositions_becomeQuotesAtTheStatementToDateWithMetadata() {
        assertThat(report.quotes()).hasSize(2);
        var nvda = report.quotes().stream()
                .filter(q -> q.instrument().isin().equals("US67066G1040")).findFirst().orElseThrow();
        assertThat(nvda.price()).isEqualByComparingTo("134.29");
        assertThat(nvda.quoteDate()).isEqualTo(LocalDate.of(2024, 12, 31));
        assertThat(nvda.instrument().currency()).isEqualTo("USD");
        assertThat(nvda.instrument().exchange()).isEqualTo("NASDAQ");
        assertThat(nvda.instrument().figi()).isEqualTo("BBG000BBJQV0");
    }

    @Test
    void conversionRates_keepOnlyTheReportCurrenciesNormalizedToEur() {
        // GBP y USD aparecen en el informe; CHF no → se filtra (§9).
        assertThat(report.exchangeRates())
                .extracting(ExchangeRate::fromCurrency)
                .containsExactlyInAnyOrder("GBP", "USD", "USD");
        assertThat(report.exchangeRates())
                .allSatisfy(rate -> assertThat(rate.toCurrency()).isEqualTo("EUR"));
    }

    @Test
    void unsupportedRows_areReportedAsErrorsWithoutAbortingTheRest() {
        assertThat(report.errors()).hasSize(3);
        assertThat(report.errors()).anySatisfy(error -> {
            assertThat(error.section()).isEqualTo("Trades");
            assertThat(error.reference()).contains("800000001");
        });
        assertThat(report.errors()).anySatisfy(error ->
                assertThat(error.section()).isEqualTo("CashTransactions"));
        assertThat(report.errors()).anySatisfy(error -> {
            assertThat(error.section()).isEqualTo("CorporateActions");
            assertThat(error.reference()).contains("9999999999");
        });
    }

    @Test
    void unreadableFile_raisesAValidationError() {
        assertThatThrownBy(() -> new FlexReportParser().read(new MockMultipartFile(
                "file", "flex.xml", "text/xml", "esto no es xml".getBytes())))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void statementWithoutAccountInformation_raisesAValidationError() {
        String xml = """
                <FlexQueryResponse queryName="q" type="AF">
                <FlexStatements count="1">
                <FlexStatement accountId="U1" fromDate="2024-01-01" toDate="2024-12-31" />
                </FlexStatements>
                </FlexQueryResponse>
                """;
        assertThatThrownBy(() -> new FlexReportParser().read(new MockMultipartFile(
                "file", "flex.xml", "text/xml", xml.getBytes())))
                .isInstanceOf(ValidationException.class);
    }
}
