package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.investments.domain.PriceQuote;
import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.SecurityId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapter test (Level 2) against real PostgreSQL: the
 * {@link PriceQuotePersistenceAdapter} honours the repository contract — the
 * write is an upsert on the natural key (security, date): a newer value for the
 * same key overwrites, never duplicates (RN-9) — and the valuation read picks
 * the latest quote ≤ the asked date (RN-6).
 */
@Import({PriceQuotePersistenceAdapter.class,
        SecurityPersistenceAdapter.class, SecurityJpaMapper.class})
class PriceQuotePersistenceAdapterTest extends PostgresTestBase {

    @Autowired private PriceQuotePersistenceAdapter adapter;
    @Autowired private PriceQuoteJpaRepository jpa;
    @Autowired private SecurityPersistenceAdapter securities;

    private SecurityId securityId;

    @BeforeEach
    void persistSecurity() {
        securityId = securities.save(Security.create(
                "IE00BK5BQT80", "EUR", "Vanguard FTSE All-World", null, "ETF", null, null)).id();
    }

    @Test
    void upsert_insertsANewQuote() {
        adapter.upsert(PriceQuote.of(securityId, LocalDate.of(2024, 3, 15), "104.52"));

        assertThat(adapter.find(securityId, LocalDate.of(2024, 3, 15)))
                .hasValueSatisfying(q -> assertThat(q.price()).isEqualByComparingTo("104.52"));
    }

    @Test
    void upsert_overwritesTheSameKeyWithoutDuplicating() {
        LocalDate date = LocalDate.of(2024, 3, 15);
        adapter.upsert(PriceQuote.of(securityId, date, "104.52"));
        adapter.upsert(PriceQuote.of(securityId, date, "105.10"));
        jpa.flush();

        assertThat(adapter.find(securityId, date))
                .hasValueSatisfying(q -> assertThat(q.price()).isEqualByComparingTo("105.10"));
        assertThat(jpa.count()).isEqualTo(1);
    }

    @Test
    void find_isEmptyForAnUnknownKey() {
        adapter.upsert(PriceQuote.of(securityId, LocalDate.of(2024, 3, 15), "104.52"));

        assertThat(adapter.find(securityId, LocalDate.of(2024, 3, 16))).isEmpty();
        assertThat(adapter.find(new SecurityId(-1L), LocalDate.of(2024, 3, 15))).isEmpty();
    }

    @Test
    void findLatestOnOrBefore_picksTheClosestQuoteNotAfterTheDate() {
        adapter.upsert(PriceQuote.of(securityId, LocalDate.of(2024, 3, 10), "100"));
        adapter.upsert(PriceQuote.of(securityId, LocalDate.of(2024, 3, 15), "104.52"));
        adapter.upsert(PriceQuote.of(securityId, LocalDate.of(2024, 3, 20), "108"));

        assertThat(adapter.findLatestOnOrBefore(securityId, LocalDate.of(2024, 3, 18)))
                .hasValueSatisfying(q -> {
                    assertThat(q.quoteDate()).isEqualTo(LocalDate.of(2024, 3, 15));
                    assertThat(q.price()).isEqualByComparingTo("104.52");
                });
        assertThat(adapter.findLatestOnOrBefore(securityId, LocalDate.of(2024, 3, 20)))
                .hasValueSatisfying(q -> assertThat(q.price()).isEqualByComparingTo("108"));
    }

    @Test
    void findLatestOnOrBefore_isEmptyWithoutAnyQuoteBeforeTheDate() {
        adapter.upsert(PriceQuote.of(securityId, LocalDate.of(2024, 3, 15), "104.52"));

        assertThat(adapter.findLatestOnOrBefore(securityId, LocalDate.of(2024, 3, 14))).isEmpty();
    }
}
