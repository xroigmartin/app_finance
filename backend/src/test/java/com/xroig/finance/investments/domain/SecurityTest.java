package com.xroig.finance.investments.domain;

import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link Security} aggregate (H1.2): identity ISIN + quote
 * currency, required fields, optional metadata ({@code ticker}/{@code exchange}/
 * {@code figi}, §9) and the non-identity metadata refresh on reimport (RN-9).
 */
class SecurityTest {

    @Test
    void createsWithIdentityAndMetadata() {
        Security security = Security.create(
                "IE00BK5BQT80", "USD", "Vanguard FTSE All-World", "VWCE", "ETF", "AEB", "BBG00LPXX872");

        assertThat(security.id()).isNull();
        assertThat(security.isin()).isEqualTo("IE00BK5BQT80");
        assertThat(security.currency()).isEqualTo("USD");
        assertThat(security.name()).isEqualTo("Vanguard FTSE All-World");
        assertThat(security.ticker()).isEqualTo("VWCE");
        assertThat(security.type()).isEqualTo("ETF");
        assertThat(security.exchange()).isEqualTo("AEB");
        assertThat(security.figi()).isEqualTo("BBG00LPXX872");
    }

    @Test
    void metadataIsOptional() {
        Security security = Security.create("IE00BK5BQT80", "EUR", "Vanguard FTSE All-World",
                null, null, null, null);

        assertThat(security.ticker()).isNull();
        assertThat(security.type()).isNull();
        assertThat(security.exchange()).isNull();
        assertThat(security.figi()).isNull();
    }

    @Test
    void normalizesIsinAndCurrency() {
        Security security = Security.create(" ie00bk5bqt80 ", "eur", "Vanguard", null, null, null, null);

        assertThat(security.isin()).isEqualTo("IE00BK5BQT80");
        assertThat(security.currency()).isEqualTo("EUR");
    }

    @Test
    void requiresIsinNameAndIsoCurrency() {
        assertThatThrownBy(() -> Security.create(null, "EUR", "Vanguard", null, null, null, null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Security.create("  ", "EUR", "Vanguard", null, null, null, null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Security.create("IE00BK5BQT80", "EUR", "  ", null, null, null, null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Security.create("IE00BK5BQT80", "ZZZ", "Vanguard", null, null, null, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void refreshesNonIdentityMetadata() {
        Security security = Security.create("IE00BK5BQT80", "USD", "Old name", "OLD", "ETF", "AEB", "FIGI1");

        security.refreshMetadata("New name", "VWCE", "SBF", "FIGI2");

        assertThat(security.name()).isEqualTo("New name");
        assertThat(security.ticker()).isEqualTo("VWCE");
        assertThat(security.exchange()).isEqualTo("SBF");
        assertThat(security.figi()).isEqualTo("FIGI2");
        // La identidad ISIN+divisa nunca cambia (RN-9).
        assertThat(security.isin()).isEqualTo("IE00BK5BQT80");
        assertThat(security.currency()).isEqualTo("USD");
    }

    @Test
    void refreshKeepsCurrentValuesWhenIncomingIsMissing() {
        Security security = Security.create("IE00BK5BQT80", "USD", "Name", "VWCE", "ETF", "AEB", "FIGI1");

        security.refreshMetadata(null, null, null, null);

        assertThat(security.name()).isEqualTo("Name");
        assertThat(security.ticker()).isEqualTo("VWCE");
        assertThat(security.exchange()).isEqualTo("AEB");
        assertThat(security.figi()).isEqualTo("FIGI1");
    }

    @Test
    void rehydrateRequiresIdentity() {
        Security security = Security.rehydrate(new SecurityId(7L),
                "IE00BK5BQT80", "USD", "Vanguard", "VWCE", "ETF", "AEB", "FIGI1");
        assertThat(security.id()).isEqualTo(new SecurityId(7L));

        assertThatIllegalArgumentException().isThrownBy(() -> Security.rehydrate(null,
                "IE00BK5BQT80", "USD", "Vanguard", "VWCE", "ETF", "AEB", "FIGI1"));
    }
}
