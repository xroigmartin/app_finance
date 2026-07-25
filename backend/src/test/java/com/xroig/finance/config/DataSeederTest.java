package com.xroig.finance.config;

import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaRepository;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaEntity;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bootstrap + full-context coverage: boots the <b>whole</b> application context against a
 * real PostgreSQL so every bean wires up (the only test that does so; the slices only load
 * fragments) and the {@link DataSeeder} {@code CommandLineRunner} actually runs.
 *
 * <p>It uses its <b>own</b> container, deliberately not the shared singleton of
 * {@code PostgresTestBase}: the runner <em>commits</em> the seeded rows on startup (it runs
 * outside any test transaction), which would leak into the roll-back-isolated repository
 * tests and break their exact-count assertions.
 *
 * <p>Re-invoking the runner pins its idempotency guard ({@code categories.count()==0}).
 */
@SpringBootTest
class DataSeederTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private AccountJpaRepository accounts;
    @Autowired private CategoryJpaRepository categories;
    @Autowired private CommandLineRunner seed; // the DataSeeder bean

    @Test
    void seedsDefaultGlobalCategoriesOnStartup() {
        assertThat(categories.findAll())
                .filteredOn(c -> c.getParent() == null && c.getAccount() == null)
                .extracting(CategoryJpaEntity::getName)
                .contains("Nómina", "Otros ingresos", "Vivienda", "Alimentación",
                        "Transporte", "Ocio", "Salud", "Suscripciones", "Otros gastos");
    }

    @Test
    void seedsNoDemoData_freshDatabaseHasNoAccounts() {
        assertThat(accounts.count()).isZero();
    }

    @Test
    void runnerIsIdempotent_secondRunSeedsNothingMore() throws Exception {
        long cats = categories.count();

        seed.run(); // categories.count()!=0 → the guard skips

        assertThat(categories.count()).isEqualTo(cats);
    }
}
