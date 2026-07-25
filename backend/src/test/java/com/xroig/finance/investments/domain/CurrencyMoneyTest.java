package com.xroig.finance.investments.domain;

import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link CurrencyMoney} value object (H1.1): an amount tied to
 * an explicit ISO 4217 currency, normalized to the monetary scale of the
 * investments context (4 decimals), with same-currency-only arithmetic.
 */
class CurrencyMoneyTest {

    @Test
    void normalizesToFourDecimalsHalfUp() {
        assertThat(CurrencyMoney.of("100", "EUR").amount()).isEqualByComparingTo("100.0000");
        assertThat(CurrencyMoney.of("1.00005", "USD").amount()).isEqualByComparingTo("1.0001"); // HALF_UP
    }

    @Test
    void normalizesCurrencyToUppercase() {
        assertThat(CurrencyMoney.of("10", "usd").currency()).isEqualTo("USD");
    }

    @Test
    void rejectsMissingOrNonIsoCurrency() {
        assertThatThrownBy(() -> CurrencyMoney.of("10", null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> CurrencyMoney.of("10", "EU"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> CurrencyMoney.of("10", "ZZZ"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void equalityIsValueBasedOnAmountAndCurrency() {
        assertThat(CurrencyMoney.of("100", "EUR")).isEqualTo(CurrencyMoney.of("100.00", "EUR"));
        assertThat(CurrencyMoney.of("100", "EUR")).hasSameHashCodeAs(CurrencyMoney.of("100.00", "EUR"));
        assertThat(CurrencyMoney.of("100", "EUR")).isNotEqualTo(CurrencyMoney.of("100.01", "EUR"));
        assertThat(CurrencyMoney.of("100", "EUR")).isNotEqualTo(CurrencyMoney.of("100", "USD"));
    }

    @Test
    void addAndSubtractWithinTheSameCurrency() {
        assertThat(CurrencyMoney.of("100", "USD").add(CurrencyMoney.of("50.5", "USD")))
                .isEqualTo(CurrencyMoney.of("150.50", "USD"));
        assertThat(CurrencyMoney.of("100", "USD").subtract(CurrencyMoney.of("30", "USD")))
                .isEqualTo(CurrencyMoney.of("70", "USD"));
    }

    @Test
    void rejectsArithmeticAcrossCurrencies() {
        CurrencyMoney eur = CurrencyMoney.of("10", "EUR");
        CurrencyMoney usd = CurrencyMoney.of("10", "USD");
        assertThatThrownBy(() -> eur.add(usd)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> eur.subtract(usd)).isInstanceOf(ValidationException.class);
    }

    @Test
    void negateAbsAndSignHelpers() {
        assertThat(CurrencyMoney.of("40", "EUR").negate()).isEqualTo(CurrencyMoney.of("-40", "EUR"));
        assertThat(CurrencyMoney.of("-40", "EUR").abs()).isEqualTo(CurrencyMoney.of("40", "EUR"));
        assertThat(CurrencyMoney.of("10", "EUR").isPositive()).isTrue();
        assertThat(CurrencyMoney.of("-10", "EUR").isNegative()).isTrue();
        assertThat(CurrencyMoney.zero("EUR").isZero()).isTrue();
    }

    @Test
    void zeroFactoryKeepsTheCurrency() {
        assertThat(CurrencyMoney.zero("USD").currency()).isEqualTo("USD");
        assertThat(CurrencyMoney.zero("USD").amount()).isEqualByComparingTo("0");
    }

    @Test
    void toStringShowsPlainAmountAndCurrency() {
        assertThat(CurrencyMoney.of("1234.5", "USD").toString()).isEqualTo("1234.5000 USD");
    }
}
