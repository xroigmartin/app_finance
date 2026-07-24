package com.xroig.finance.investments.domain;

import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link InvestmentTransaction} aggregate (H1.3): the eleven
 * operation types (§3), the cash-flow sign convention per type (amounts stored
 * with the sign of the real cash flow, so cash RN-2 and positions RN-3 are direct
 * sums), the conditional fields (security null on pure cash operations, counter
 * leg only on FX_TRADE, fee/tax with their own currency) and the optional
 * {@code external_id} (RN-10, null on manual entries).
 */
class InvestmentTransactionTest {

    private static final PortfolioId PORTFOLIO = new PortfolioId(1L);
    private static final SecurityId SECURITY = new SecurityId(10L);
    private static final LocalDate DATE = LocalDate.of(2025, 6, 2);

    private static CurrencyMoney usd(String amount) {
        return CurrencyMoney.of(amount, "USD");
    }

    private static InvestmentTransaction.Builder validBuy() {
        return InvestmentTransaction.builder()
                .portfolio(PORTFOLIO)
                .type(InvestmentTransactionType.BUY)
                .tradeDate(DATE)
                .security(SECURITY)
                .quantity(Quantity.of("2.303"))
                .price("133.5")
                .amount(usd("-307.45"));
    }

    private static InvestmentTransaction.Builder validCash(InvestmentTransactionType type, String amount) {
        return InvestmentTransaction.builder()
                .portfolio(PORTFOLIO)
                .type(type)
                .tradeDate(DATE)
                .amount(usd(amount));
    }

    @Test
    void buildsABuyWithAllFields() {
        InvestmentTransaction buy = validBuy()
                .fee(CurrencyMoney.of("-1.25", "EUR"))
                .fxRateToBase("0.921")
                .description("Compra VWCE")
                .externalId("ORD-123")
                .build();

        assertThat(buy.id()).isNull();
        assertThat(buy.portfolioId()).isEqualTo(PORTFOLIO);
        assertThat(buy.securityId()).isEqualTo(SECURITY);
        assertThat(buy.type()).isEqualTo(InvestmentTransactionType.BUY);
        assertThat(buy.tradeDate()).isEqualTo(DATE);
        assertThat(buy.quantity()).isEqualTo(Quantity.of("2.303"));
        assertThat(buy.price()).isEqualByComparingTo("133.50000000");
        assertThat(buy.amount()).isEqualTo(usd("-307.45"));
        assertThat(buy.currency()).isEqualTo("USD");
        assertThat(buy.fee()).isEqualTo(CurrencyMoney.of("-1.25", "EUR"));
        assertThat(buy.tax()).isNull();
        assertThat(buy.fxRateToBase()).isEqualByComparingTo("0.92100000");
        assertThat(buy.description()).isEqualTo("Compra VWCE");
        assertThat(buy.externalId()).isEqualTo("ORD-123");
    }

    @Test
    void externalIdIsOptionalAndBlankBecomesNull() {
        assertThat(validBuy().build().externalId()).isNull();
        assertThat(validBuy().externalId("  ").build().externalId()).isNull();
    }

    @Test
    void buyAndSellFollowTheSignConvention() {
        // BUY: sale efectivo (amount < 0), entran títulos (quantity > 0).
        assertThatThrownBy(() -> validBuy().amount(usd("307.45")).build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validBuy().quantity(Quantity.of("-2")).build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validBuy().quantity(Quantity.ZERO).build())
                .isInstanceOf(ValidationException.class);

        // SELL: entra efectivo (amount > 0), salen títulos (quantity < 0).
        InvestmentTransaction.Builder sell = validBuy()
                .type(InvestmentTransactionType.SELL)
                .quantity(Quantity.of("-2.303"))
                .amount(usd("310"));
        assertThat(sell.build().type()).isEqualTo(InvestmentTransactionType.SELL);
        assertThatThrownBy(() -> sell.amount(usd("-310")).build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> sell.amount(usd("310")).quantity(Quantity.of("2.303")).build())
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void cashFlowsFollowTheSignConventionByDefault() {
        // Entra efectivo: DIVIDEND / INTEREST / DEPOSIT > 0.
        assertThat(validCash(InvestmentTransactionType.DIVIDEND, "12.5").security(SECURITY).build()
                .amount().isPositive()).isTrue();
        assertThatThrownBy(() -> validCash(InvestmentTransactionType.DEPOSIT, "-1000").build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validCash(InvestmentTransactionType.INTEREST, "0").build())
                .isInstanceOf(ValidationException.class);

        // Sale efectivo: WITHDRAWAL / FEE / TAX / TRADE_TAX < 0.
        assertThat(validCash(InvestmentTransactionType.WITHDRAWAL, "-500").build()
                .amount().isNegative()).isTrue();
        assertThatThrownBy(() -> validCash(InvestmentTransactionType.WITHDRAWAL, "500").build())
                .isInstanceOf(ValidationException.class);
        assertThat(validCash(InvestmentTransactionType.TAX, "-1.9").security(SECURITY).build()
                .amount().isNegative()).isTrue();
        assertThatThrownBy(() -> validCash(InvestmentTransactionType.TRADE_TAX, "0.2").security(SECURITY).build())
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void dividendInterestFeeAndTaxAllowTheOppositeSignForBrokerReversalsButNeverZero() {
        // IBKR puede emitir una reversa (Flex: apunte original + reversa de signo
        // invertido + re-book) para estos cuatro tipos de apunte de efectivo puro
        // (docs/prd/inversiones.md §11). El signo por defecto de §3 sigue siendo el
        // caso normal, pero el inverso no viola la invariante: solo el cero lo hace.
        assertThat(validCash(InvestmentTransactionType.DIVIDEND, "-12.5").security(SECURITY).build()
                .amount().isNegative()).isTrue();
        assertThat(validCash(InvestmentTransactionType.INTEREST, "-3.2").build()
                .amount().isNegative()).isTrue();
        assertThat(validCash(InvestmentTransactionType.FEE, "3").build()
                .amount().isPositive()).isTrue();
        assertThat(validCash(InvestmentTransactionType.TAX, "1.9").security(SECURITY).build()
                .amount().isPositive()).isTrue();

        assertThatThrownBy(() -> validCash(InvestmentTransactionType.DIVIDEND, "0").security(SECURITY).build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validCash(InvestmentTransactionType.INTEREST, "0").build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validCash(InvestmentTransactionType.FEE, "0").build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validCash(InvestmentTransactionType.TAX, "0").security(SECURITY).build())
                .isInstanceOf(ValidationException.class);

        // TRADE_TAX y DEPOSIT/WITHDRAWAL no forman parte de esta relajación: el
        // primero es una tasa de compraventa (no un apunte de Cash Transactions
        // sujeto a reversa), y los otros dos derivan su tipo del propio signo.
        assertThatThrownBy(() -> validCash(InvestmentTransactionType.TRADE_TAX, "0.2").security(SECURITY).build())
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void splitIsASignedQuantityDeltaWithoutCashFlow() {
        InvestmentTransaction.Builder split = InvestmentTransaction.builder()
                .portfolio(PORTFOLIO)
                .type(InvestmentTransactionType.SPLIT)
                .tradeDate(DATE)
                .security(SECURITY)
                .quantity(Quantity.of("18"))
                .amount(usd("0"));

        assertThat(split.build().quantity()).isEqualTo(Quantity.of("18"));
        // Reverse split: delta negativo, también válido.
        assertThat(split.quantity(Quantity.of("-18")).build().quantity()).isEqualTo(Quantity.of("-18"));

        assertThatThrownBy(() -> split.quantity(Quantity.ZERO).build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> split.quantity(Quantity.of("18")).amount(usd("-10")).build())
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void fxTradeHasAnOutgoingAndAnIncomingLeg() {
        InvestmentTransaction.Builder fx = InvestmentTransaction.builder()
                .portfolio(PORTFOLIO)
                .type(InvestmentTransactionType.FX_TRADE)
                .tradeDate(DATE)
                .amount(CurrencyMoney.of("-1000", "EUR"))
                .counterAmount(usd("1080"));

        InvestmentTransaction trade = fx.build();
        assertThat(trade.amount()).isEqualTo(CurrencyMoney.of("-1000", "EUR"));
        assertThat(trade.counterAmount()).isEqualTo(usd("1080"));

        // Pierna saliente negativa, entrante positiva, divisas distintas, y ambas presentes.
        assertThatThrownBy(() -> fx.amount(CurrencyMoney.of("1000", "EUR")).build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> fx.amount(CurrencyMoney.of("-1000", "EUR")).counterAmount(usd("-1080")).build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> fx.counterAmount(CurrencyMoney.of("1080", "EUR")).build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> fx.counterAmount(null).build())
                .isInstanceOf(ValidationException.class);
        // Y la pierna entrante solo existe en FX_TRADE.
        assertThatThrownBy(() -> validBuy().counterAmount(usd("10")).build())
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void quantityOnlyExistsOnBuySellAndSplit() {
        assertThatThrownBy(() -> validCash(InvestmentTransactionType.DIVIDEND, "12.5")
                .security(SECURITY).quantity(Quantity.of("1")).build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validCash(InvestmentTransactionType.DEPOSIT, "1000")
                .quantity(Quantity.of("1")).build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validBuy().quantity(null).build())
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void securityIsRequiredOnInstrumentOperations() {
        assertThatThrownBy(() -> validBuy().security(null).build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validCash(InvestmentTransactionType.DIVIDEND, "12.5").build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validCash(InvestmentTransactionType.TRADE_TAX, "-0.2").build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> InvestmentTransaction.builder()
                .portfolio(PORTFOLIO).type(InvestmentTransactionType.SPLIT).tradeDate(DATE)
                .quantity(Quantity.of("18")).amount(usd("0")).build())
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void securityIsForbiddenOnPureCashOperations() {
        assertThatThrownBy(() -> validCash(InvestmentTransactionType.DEPOSIT, "1000").security(SECURITY).build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validCash(InvestmentTransactionType.WITHDRAWAL, "-500").security(SECURITY).build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validCash(InvestmentTransactionType.FX_TRADE, "-1000")
                .counterAmount(CurrencyMoney.of("1080", "GBP")).security(SECURITY).build())
                .isInstanceOf(ValidationException.class);

        // En INTEREST / FEE / TAX el instrumento es opcional (interés de bróker vs cupón).
        assertThat(validCash(InvestmentTransactionType.INTEREST, "3.2").build().securityId()).isNull();
        assertThat(validCash(InvestmentTransactionType.TAX, "-1.9").security(SECURITY).build().securityId())
                .isEqualTo(SECURITY);
    }

    @Test
    void feeAndTaxCarryTheCashFlowSign() {
        assertThatThrownBy(() -> validBuy().fee(CurrencyMoney.of("1.25", "EUR")).build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validBuy().tax(CurrencyMoney.of("0", "EUR")).build())
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void fxRateToBaseSnapshotIsPositiveWhenPresent() {
        assertThat(validBuy().build().fxRateToBase()).isNull(); // apunte manual: sin snapshot (RN-7b fallback)
        assertThatThrownBy(() -> validBuy().fxRateToBase("0").build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validBuy().fxRateToBase("-0.9").build())
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void requiresPortfolioTypeDateAndAmount() {
        assertThatThrownBy(() -> validBuy().portfolio(null).build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validBuy().type(null).build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validBuy().tradeDate(null).build())
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validBuy().amount(null).build())
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rehydrateRequiresIdentity() {
        InvestmentTransaction buy = validBuy().rehydrate(new InvestmentTransactionId(9L));
        assertThat(buy.id()).isEqualTo(new InvestmentTransactionId(9L));

        assertThatIllegalArgumentException().isThrownBy(() -> validBuy().rehydrate(null));
    }
}
