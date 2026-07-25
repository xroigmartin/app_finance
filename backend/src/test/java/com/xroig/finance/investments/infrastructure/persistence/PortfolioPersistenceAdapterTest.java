package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.investments.domain.Portfolio;
import com.xroig.finance.investments.domain.PortfolioId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapter test (Level 2) against real PostgreSQL: the V7 migration creates the
 * {@code investments} schema and its {@code portfolio} table, and the
 * {@link PortfolioPersistenceAdapter} + {@link PortfolioJpaMapper} round-trip
 * preserves the pure {@link Portfolio} aggregate (base currency immutable —
 * only the name changes on update).
 */
@Import({PortfolioPersistenceAdapter.class, PortfolioJpaMapper.class})
class PortfolioPersistenceAdapterTest extends PostgresTestBase {

    @Autowired private PortfolioPersistenceAdapter adapter;
    @Autowired private PortfolioJpaRepository jpa;

    @Test
    void save_assignsIdentityAndMapsBackToDomain() {
        Portfolio saved = adapter.save(Portfolio.create("Interactive Brokers", "EUR"));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.name()).isEqualTo("Interactive Brokers");
        assertThat(saved.baseCurrency()).isEqualTo("EUR");
    }

    @Test
    void findById_returnsTheStoredAggregate() {
        PortfolioId id = adapter.save(Portfolio.create("Degiro", "USD")).id();

        assertThat(adapter.findById(id)).hasValueSatisfying(p -> {
            assertThat(p.name()).isEqualTo("Degiro");
            assertThat(p.baseCurrency()).isEqualTo("USD");
        });
        assertThat(adapter.findById(new PortfolioId(-1L))).isEmpty();
    }

    @Test
    void update_persistsTheRename() {
        Portfolio saved = adapter.save(Portfolio.create("Viejo nombre", "EUR"));
        saved.rename("Nuevo nombre");

        Portfolio reloaded = adapter.save(saved);

        assertThat(reloaded.id()).isEqualTo(saved.id());
        assertThat(adapter.findById(reloaded.id())).hasValueSatisfying(p -> {
            assertThat(p.name()).isEqualTo("Nuevo nombre");
            assertThat(p.baseCurrency()).isEqualTo("EUR");
        });
    }

    @Test
    void findAll_andDeleteById() {
        PortfolioId id = adapter.save(Portfolio.create("Borrable", "EUR")).id();
        assertThat(adapter.findAll()).extracting(p -> p.id().value()).contains(id.value());

        adapter.deleteById(id);

        assertThat(jpa.existsById(id.value())).isFalse();
    }
}
