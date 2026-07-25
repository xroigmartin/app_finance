package com.xroig.finance.investments.domain;

import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link Portfolio} aggregate (H1.3): required name and ISO 4217
 * base currency. The base currency is immutable after creation — the RN-7a
 * snapshots and the import validation (§8) anchor to it.
 */
class PortfolioTest {

    @Test
    void createsWithNameAndBaseCurrency() {
        Portfolio portfolio = Portfolio.create("Interactive Brokers", "eur");

        assertThat(portfolio.id()).isNull();
        assertThat(portfolio.name()).isEqualTo("Interactive Brokers");
        assertThat(portfolio.baseCurrency()).isEqualTo("EUR");
    }

    @Test
    void requiresNameAndIsoBaseCurrency() {
        assertThatThrownBy(() -> Portfolio.create(null, "EUR"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Portfolio.create("  ", "EUR"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Portfolio.create("IB", "ZZZ"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void renames() {
        Portfolio portfolio = Portfolio.create("IB", "EUR");

        portfolio.rename("Interactive Brokers");

        assertThat(portfolio.name()).isEqualTo("Interactive Brokers");
        assertThatThrownBy(() -> portfolio.rename(" "))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rehydrateRequiresIdentity() {
        Portfolio portfolio = Portfolio.rehydrate(new PortfolioId(3L), "IB", "USD");
        assertThat(portfolio.id()).isEqualTo(new PortfolioId(3L));
        assertThat(portfolio.baseCurrency()).isEqualTo("USD");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Portfolio.rehydrate(null, "IB", "USD"));
    }
}
