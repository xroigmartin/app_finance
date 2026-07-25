/**
 * Investments bounded context — domain layer. Pure of Spring and JPA: the
 * aggregates ({@link com.xroig.finance.investments.domain.Portfolio},
 * {@link com.xroig.finance.investments.domain.Security},
 * {@link com.xroig.finance.investments.domain.InvestmentTransaction}) and values
 * ({@link com.xroig.finance.investments.domain.CurrencyMoney},
 * {@link com.xroig.finance.investments.domain.Quantity},
 * {@link com.xroig.finance.investments.domain.PriceQuote},
 * {@link com.xroig.finance.investments.domain.ExchangeRate}), the domain services
 * ({@link com.xroig.finance.investments.domain.PositionCalculator},
 * {@link com.xroig.finance.investments.domain.CurrencyConverter}) and the outbound
 * ports the infrastructure implements. Everything is computed, never stored (§3
 * of the PRD); every amount carries its currency and the cash-flow sign.
 */
package com.xroig.finance.investments.domain;
