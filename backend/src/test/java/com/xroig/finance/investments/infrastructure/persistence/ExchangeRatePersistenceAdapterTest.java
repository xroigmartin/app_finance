package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.investments.domain.ExchangeRate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapter test (Level 2) against real PostgreSQL: the
 * {@link ExchangeRatePersistenceAdapter} honours the repository contract — the
 * write is an upsert on the natural key (date, pair): a newer value for the same
 * key overwrites, never duplicates (RN-9) — and {@code findAll} feeds the
 * read-side {@code CurrencyConverter}.
 */
@Import(ExchangeRatePersistenceAdapter.class)
class ExchangeRatePersistenceAdapterTest extends PostgresTestBase {

    @Autowired private ExchangeRatePersistenceAdapter adapter;
    @Autowired private ExchangeRateJpaRepository jpa;

    @Test
    void upsert_insertsANewRate() {
        adapter.upsert(ExchangeRate.toEur(LocalDate.of(2024, 3, 15), "USD", "0.92345678"));

        assertThat(adapter.find(LocalDate.of(2024, 3, 15), "USD", "EUR"))
                .hasValueSatisfying(r -> assertThat(r.rate()).isEqualByComparingTo("0.92345678"));
    }

    @Test
    void upsert_overwritesTheSameKeyWithoutDuplicating() {
        LocalDate date = LocalDate.of(2024, 3, 15);
        adapter.upsert(ExchangeRate.toEur(date, "USD", "0.92"));
        adapter.upsert(ExchangeRate.toEur(date, "USD", "0.93"));
        jpa.flush();

        assertThat(adapter.find(date, "USD", "EUR"))
                .hasValueSatisfying(r -> assertThat(r.rate()).isEqualByComparingTo("0.93"));
        assertThat(jpa.count()).isEqualTo(1);
    }

    @Test
    void upsert_keepsDistinctKeysApart() {
        adapter.upsert(ExchangeRate.toEur(LocalDate.of(2024, 3, 15), "USD", "0.92"));
        adapter.upsert(ExchangeRate.toEur(LocalDate.of(2024, 3, 16), "USD", "0.925"));
        adapter.upsert(ExchangeRate.toEur(LocalDate.of(2024, 3, 15), "GBP", "1.17"));

        assertThat(jpa.count()).isEqualTo(3);
    }

    @Test
    void find_isEmptyForAnUnknownKey() {
        adapter.upsert(ExchangeRate.toEur(LocalDate.of(2024, 3, 15), "USD", "0.92"));

        assertThat(adapter.find(LocalDate.of(2024, 3, 16), "USD", "EUR")).isEmpty();
        assertThat(adapter.find(LocalDate.of(2024, 3, 15), "GBP", "EUR")).isEmpty();
    }

    @Test
    void findAll_returnsEveryStoredRate() {
        adapter.upsert(ExchangeRate.toEur(LocalDate.of(2024, 3, 15), "USD", "0.92"));
        adapter.upsert(ExchangeRate.toEur(LocalDate.of(2024, 3, 15), "GBP", "1.17"));

        assertThat(adapter.findAll())
                .extracting(ExchangeRate::fromCurrency)
                .containsExactlyInAnyOrder("USD", "GBP");
    }
}
