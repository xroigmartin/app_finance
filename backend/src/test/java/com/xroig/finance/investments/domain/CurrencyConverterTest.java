package com.xroig.finance.investments.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link CurrencyConverter} domain service (H1.2): valuation
 * conversion (RN-7b) with the latest stored rate ≤ the requested date, rates stored
 * currency→EUR and any other pair derived via the EUR pivot
 * ({@code from→to = (from→EUR) ÷ (to→EUR)}). A missing rate yields empty — the
 * read side decides the fallback (RN-6 style degradation), not the service.
 */
class CurrencyConverterTest {

    private static final LocalDate JAN_10 = LocalDate.of(2025, 1, 10);
    private static final LocalDate FEB_1 = LocalDate.of(2025, 2, 1);
    private static final LocalDate MAR_1 = LocalDate.of(2025, 3, 1);
    private static final LocalDate MAR_5 = LocalDate.of(2025, 3, 5);

    private final CurrencyConverter converter = new CurrencyConverter(List.of(
            ExchangeRate.toEur(JAN_10, "USD", "0.9"),
            ExchangeRate.toEur(MAR_1, "USD", "0.95"),
            ExchangeRate.toEur(JAN_10, "GBP", "1.2")));

    @Test
    void sameCurrencyNeedsNoRate() {
        CurrencyMoney amount = CurrencyMoney.of("100", "USD");
        assertThat(new CurrencyConverter(List.of()).convert(amount, "USD", FEB_1))
                .contains(amount);
    }

    @Test
    void convertsToEurWithLatestRateOnOrBeforeDate() {
        assertThat(converter.convert(CurrencyMoney.of("100", "USD"), "EUR", FEB_1))
                .contains(CurrencyMoney.of("90", "EUR"));
        // En la fecha exacta del tipo más reciente, usa ese tipo.
        assertThat(converter.convert(CurrencyMoney.of("100", "USD"), "EUR", MAR_1))
                .contains(CurrencyMoney.of("95", "EUR"));
        // Un tipo posterior a la fecha pedida no se usa.
        assertThat(converter.convert(CurrencyMoney.of("100", "USD"), "EUR", MAR_5))
                .contains(CurrencyMoney.of("95", "EUR"));
    }

    @Test
    void convertsFromEurWithTheInverseRate() {
        assertThat(converter.convert(CurrencyMoney.of("90", "EUR"), "USD", FEB_1))
                .contains(CurrencyMoney.of("100", "USD"));
    }

    @Test
    void derivesCrossPairsViaEurPivot() {
        // USD→GBP = (USD→EUR 0.9) ÷ (GBP→EUR 1.2)
        assertThat(converter.convert(CurrencyMoney.of("120", "USD"), "GBP", FEB_1))
                .contains(CurrencyMoney.of("90", "GBP"));
    }

    @Test
    void emptyWhenNoRateOnOrBeforeDate() {
        assertThat(converter.convert(CurrencyMoney.of("100", "USD"), "EUR", JAN_10.minusDays(1)))
                .isEmpty();
        // Sin tipo para la divisa destino, el par cruzado tampoco se puede derivar.
        assertThat(converter.convert(CurrencyMoney.of("100", "USD"), "CHF", FEB_1))
                .isEmpty();
    }

    @Test
    void normalizesTheTargetCurrency() {
        assertThat(converter.convert(CurrencyMoney.of("100", "USD"), "eur", FEB_1))
                .contains(CurrencyMoney.of("90", "EUR"));
    }
}
