package com.xroig.finance.investments.domain;

import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link ExchangeRate} value (H1.2): a daily rate normalized to
 * the single direction currency→EUR (EUR is the pivot of every derived pair, RN-7).
 * Uniqueness per (date, pair) and upsert semantics (RN-9) are the repository's
 * contract, verified at the persistence adapter.
 */
class ExchangeRateTest {

    private static final LocalDate DATE = LocalDate.of(2025, 12, 31);

    @Test
    void createsNormalizedToEurDirection() {
        ExchangeRate rate = ExchangeRate.toEur(DATE, "usd", "0.921");

        assertThat(rate.rateDate()).isEqualTo(DATE);
        assertThat(rate.fromCurrency()).isEqualTo("USD");
        assertThat(rate.toCurrency()).isEqualTo("EUR");
        assertThat(rate.rate()).isEqualByComparingTo("0.92100000");
    }

    @Test
    void equalityIsValueBasedAcrossScales() {
        assertThat(ExchangeRate.toEur(DATE, "USD", "0.9"))
                .isEqualTo(ExchangeRate.toEur(DATE, "USD", "0.90000000"));
    }

    @Test
    void rejectsEurToEurAndUnknownCurrency() {
        assertThatThrownBy(() -> ExchangeRate.toEur(DATE, "EUR", "1"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ExchangeRate.toEur(DATE, "ZZZ", "1"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ExchangeRate.toEur(DATE, null, "1"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void requiresDateAndPositiveRate() {
        assertThatThrownBy(() -> ExchangeRate.toEur(null, "USD", "0.9"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ExchangeRate.toEur(DATE, "USD", "0"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ExchangeRate.toEur(DATE, "USD", "-0.9"))
                .isInstanceOf(ValidationException.class);
    }
}
