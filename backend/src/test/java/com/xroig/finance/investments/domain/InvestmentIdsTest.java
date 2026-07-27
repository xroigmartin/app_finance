package com.xroig.finance.investments.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for the typed identifiers of the investments context (H1.1):
 * {@link PortfolioId}, {@link SecurityId} and {@link InvestmentTransactionId},
 * following the same contract as the other contexts' ids (non-null value,
 * value-based equality).
 */
class InvestmentIdsTest {

    @Test
    void exposeTheirValueAndAreValueBased() {
        assertThat(new PortfolioId(1L).value()).isEqualTo(1L);
        assertThat(new PortfolioId(1L)).isEqualTo(new PortfolioId(1L));
        assertThat(new SecurityId(2L)).isEqualTo(new SecurityId(2L));
        assertThat(new SecurityId(2L)).isNotEqualTo(new SecurityId(3L));
        assertThat(new InvestmentTransactionId(4L).value()).isEqualTo(4L);
    }

    @Test
    void rejectNullValues() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PortfolioId(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new SecurityId(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new InvestmentTransactionId(null));
    }
}
