package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.SecurityId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adapter test (Level 2) against real PostgreSQL: the
 * {@link SecurityPersistenceAdapter} + {@link SecurityJpaMapper} round-trip
 * preserves the pure {@link Security} aggregate (identity ISIN+currency plus
 * non-identity metadata), the business-identity lookup works, and the physical
 * backstop of the identity — {@code UNIQUE (isin, currency)} in the V7
 * migration — rejects a duplicate.
 */
@Import({SecurityPersistenceAdapter.class, SecurityJpaMapper.class})
class SecurityPersistenceAdapterTest extends PostgresTestBase {

    @Autowired private SecurityPersistenceAdapter adapter;
    @Autowired private SecurityJpaRepository jpa;

    @Test
    void save_assignsIdentityAndMapsEveryFieldBack() {
        Security saved = adapter.save(Security.create(
                "IE00BK5BQT80", "EUR", "Vanguard FTSE All-World", "VWCE", "ETF", "AEB", "BBG00LPTPGD4"));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.isin()).isEqualTo("IE00BK5BQT80");
        assertThat(saved.currency()).isEqualTo("EUR");
        assertThat(saved.name()).isEqualTo("Vanguard FTSE All-World");
        assertThat(saved.ticker()).isEqualTo("VWCE");
        assertThat(saved.type()).isEqualTo("ETF");
        assertThat(saved.exchange()).isEqualTo("AEB");
        assertThat(saved.figi()).isEqualTo("BBG00LPTPGD4");
    }

    @Test
    void save_keepsOptionalMetadataNull() {
        Security saved = adapter.save(Security.create(
                "US0378331005", "USD", "Apple Inc", null, null, null, null));

        assertThat(adapter.findById(saved.id())).hasValueSatisfying(s -> {
            assertThat(s.ticker()).isNull();
            assertThat(s.type()).isNull();
            assertThat(s.exchange()).isNull();
            assertThat(s.figi()).isNull();
        });
    }

    @Test
    void findByIsinAndCurrency_findsTheBusinessIdentity() {
        adapter.save(Security.create("IE00B4L5Y983", "EUR", "iShares Core MSCI World", null, "ETF", null, null));

        assertThat(adapter.findByIsinAndCurrency("IE00B4L5Y983", "EUR")).isPresent();
        assertThat(adapter.findByIsinAndCurrency("IE00B4L5Y983", "USD")).isEmpty();
        assertThat(adapter.findByIsinAndCurrency("XX0000000000", "EUR")).isEmpty();
    }

    @Test
    void update_persistsRefreshedMetadata() {
        Security saved = adapter.save(Security.create(
                "FR0000120271", "EUR", "TotalEnergies", null, null, null, null));
        saved.refreshMetadata("TotalEnergies SE", "TTE", "SBF", "BBG000C1M4K8");
        saved.changeType("Acción");

        adapter.save(saved);

        assertThat(adapter.findById(saved.id())).hasValueSatisfying(s -> {
            assertThat(s.name()).isEqualTo("TotalEnergies SE");
            assertThat(s.ticker()).isEqualTo("TTE");
            assertThat(s.exchange()).isEqualTo("SBF");
            assertThat(s.figi()).isEqualTo("BBG000C1M4K8");
            assertThat(s.type()).isEqualTo("Acción");
        });
    }

    @Test
    void duplicateIsinAndCurrency_violatesTheUniqueConstraint() {
        adapter.save(Security.create("IE00BK5BQT80", "EUR", "Vanguard FTSE All-World", null, null, null, null));

        assertThatThrownBy(() -> {
            adapter.save(Security.create("IE00BK5BQT80", "EUR", "Duplicado", null, null, null, null));
            jpa.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameIsinInAnotherCurrency_isAnotherSecurity() {
        adapter.save(Security.create("IE00BK5BQT80", "EUR", "Vanguard FTSE All-World", null, null, null, null));
        adapter.save(Security.create("IE00BK5BQT80", "USD", "Vanguard FTSE All-World (USD)", null, null, null, null));
        jpa.flush();

        assertThat(adapter.findAll()).extracting(Security::isin)
                .filteredOn("IE00BK5BQT80"::equals).hasSize(2);
    }

    @Test
    void deleteById_removesTheRow() {
        SecurityId id = adapter.save(Security.create(
                "LU0000000001", "EUR", "Borrable", null, null, null, null)).id();

        adapter.deleteById(id);

        assertThat(jpa.existsById(id.value())).isFalse();
    }
}
