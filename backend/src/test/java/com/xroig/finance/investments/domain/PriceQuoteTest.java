package com.xroig.finance.investments.domain;

import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link PriceQuote} value (H1.2): a closing price of one
 * security on one date, in the security's currency, at the price scale of the
 * context (8 decimals). Uniqueness per (security, date) and upsert semantics
 * (RN-9) are the repository's contract, verified at the persistence adapter.
 */
class PriceQuoteTest {

    private static final SecurityId SECURITY = new SecurityId(1L);
    private static final LocalDate DATE = LocalDate.of(2025, 12, 31);

    @Test
    void createsNormalizedToEightDecimals() {
        PriceQuote quote = PriceQuote.of(SECURITY, DATE, "133.505");

        assertThat(quote.securityId()).isEqualTo(SECURITY);
        assertThat(quote.quoteDate()).isEqualTo(DATE);
        assertThat(quote.price()).isEqualByComparingTo("133.50500000");
    }

    @Test
    void equalityIsValueBasedAcrossScales() {
        assertThat(PriceQuote.of(SECURITY, DATE, "133.5"))
                .isEqualTo(PriceQuote.of(SECURITY, DATE, "133.50000000"));
    }

    @Test
    void requiresSecurityDateAndPositivePrice() {
        assertThatThrownBy(() -> PriceQuote.of(null, DATE, "10"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> PriceQuote.of(SECURITY, null, "10"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> PriceQuote.of(SECURITY, DATE, "0"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> PriceQuote.of(SECURITY, DATE, "-10"))
                .isInstanceOf(ValidationException.class);
    }
}
