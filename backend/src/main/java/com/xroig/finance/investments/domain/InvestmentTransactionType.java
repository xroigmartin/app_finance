package com.xroig.finance.investments.domain;

/**
 * The operation types of a portfolio (§3) with their invariants encoded as data:
 * whether the operation carries a security, what sign the quantity must have and
 * what sign the amount must have under the cash-flow sign convention (every amount
 * is stored with the sign of the real cash flow, so cash RN-2 and positions RN-3
 * are direct sums). {@code TAX} is a withholding on income (dividends/interest);
 * {@code TRADE_TAX} is a tax on a trade (French/Italian FTT, stamp duty) — the
 * distinction keeps the income view from confusing an FTT with a withholding (§9).
 *
 * <p>{@code DIVIDEND}/{@code INTEREST}/{@code FEE}/{@code TAX} use {@code
 * AmountRule.NON_ZERO} rather than a fixed sign: IBKR's Flex can emit, for the
 * same {@code actionID}, a correction sequence (original + a reversal with the
 * opposite sign + a re-book) for these four pure cash-transaction types (docs/prd/
 * inversiones.md §11) — the reversal's real cash flow genuinely is the opposite of
 * the usual case. {@code TRADE_TAX} (not a Cash Transaction) and {@code DEPOSIT}/
 * {@code WITHDRAWAL} (whose type is itself derived from the sign, see
 * {@code FlexReportParser.cashType}) keep a fixed sign.
 */
public enum InvestmentTransactionType {

    BUY(SecurityRule.REQUIRED, QuantityRule.POSITIVE, AmountRule.NEGATIVE),
    SELL(SecurityRule.REQUIRED, QuantityRule.NEGATIVE, AmountRule.POSITIVE),
    DIVIDEND(SecurityRule.REQUIRED, QuantityRule.NONE, AmountRule.NON_ZERO),
    INTEREST(SecurityRule.OPTIONAL, QuantityRule.NONE, AmountRule.NON_ZERO),
    FEE(SecurityRule.OPTIONAL, QuantityRule.NONE, AmountRule.NON_ZERO),
    TAX(SecurityRule.OPTIONAL, QuantityRule.NONE, AmountRule.NON_ZERO),
    TRADE_TAX(SecurityRule.REQUIRED, QuantityRule.NONE, AmountRule.NEGATIVE),
    SPLIT(SecurityRule.REQUIRED, QuantityRule.NON_ZERO, AmountRule.ZERO),
    DEPOSIT(SecurityRule.FORBIDDEN, QuantityRule.NONE, AmountRule.POSITIVE),
    WITHDRAWAL(SecurityRule.FORBIDDEN, QuantityRule.NONE, AmountRule.NEGATIVE),
    FX_TRADE(SecurityRule.FORBIDDEN, QuantityRule.NONE, AmountRule.NEGATIVE);

    /** Whether the operation references an instrument. */
    enum SecurityRule { REQUIRED, OPTIONAL, FORBIDDEN }

    /** Sign of the quantity ({@code NONE} = the operation carries no quantity). */
    enum QuantityRule { POSITIVE, NEGATIVE, NON_ZERO, NONE }

    /**
     * Sign of the amount under the cash-flow convention ({@code ZERO} = no cash
     * flow; {@code NON_ZERO} = either sign, forbids only zero — broker reversals).
     */
    enum AmountRule { POSITIVE, NEGATIVE, ZERO, NON_ZERO }

    private final SecurityRule securityRule;
    private final QuantityRule quantityRule;
    private final AmountRule amountRule;

    InvestmentTransactionType(SecurityRule securityRule, QuantityRule quantityRule, AmountRule amountRule) {
        this.securityRule = securityRule;
        this.quantityRule = quantityRule;
        this.amountRule = amountRule;
    }

    SecurityRule securityRule() {
        return securityRule;
    }

    QuantityRule quantityRule() {
        return quantityRule;
    }

    AmountRule amountRule() {
        return amountRule;
    }
}
