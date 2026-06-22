package com.xroig.finance.config;

import com.xroig.finance.model.Account;
import com.xroig.finance.model.Category;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bootstrap coverage (the item E4 deferred to "a context/integration test in a
 * later level"): boots the <b>whole</b> application context against a real
 * PostgreSQL so the {@link DataSeeder} {@code CommandLineRunner} actually runs —
 * something neither the Mockito unit tests nor the {@code @WebMvcTest}/
 * {@code @DataJpaTest} slices exercise, leaving {@code DataSeeder} at 0 %.
 *
 * <p>It uses its <b>own</b> container, deliberately not the shared singleton of
 * {@code PostgresTestBase}: the runner <em>commits</em> the seeded rows on
 * startup (it runs outside any test transaction), which would leak into the
 * roll-back-isolated repository tests and break their exact-count assertions.
 *
 * <p>{@code finance.seed-demo=true} forces the demo branch (accounts + 12 months
 * of movements), and re-invoking the runner pins its idempotency guards
 * ({@code categories.count()==0} / {@code accounts.count()==0}).
 */
@SpringBootTest(properties = "finance.seed-demo=true")
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

    @Autowired private AccountRepository accounts;
    @Autowired private CategoryRepository categories;
    @Autowired private TransactionRepository transactions;
    @Autowired private CommandLineRunner seed; // the DataSeeder bean

    @Test
    void seedsDefaultGlobalCategoriesOnStartup() {
        assertThat(categories.findAll())
                .filteredOn(c -> c.getParent() == null && c.getAccount() == null)
                .extracting(Category::getName)
                .contains("Nómina", "Otros ingresos", "Vivienda", "Alimentación",
                        "Transporte", "Ocio", "Salud", "Suscripciones", "Otros gastos");
    }

    @Test
    void seedsDemoAccountsAndAnAccountScopedCategory() {
        List<Account> all = accounts.findAll();
        assertThat(all).extracting(Account::getName)
                .contains("Cuenta corriente", "Ahorro");

        Account main = all.stream().filter(a -> a.getName().equals("Cuenta corriente"))
                .findFirst().orElseThrow();
        // "Luz" is seeded bound to the main account, to showcase per-account scope.
        assertThat(categories.findAll())
                .filteredOn(c -> "Luz".equals(c.getName()))
                .singleElement()
                .satisfies(luz -> assertThat(luz.getAccount().getId()).isEqualTo(main.getId()));
    }

    @Test
    void seedsTwelveMonthsOfDemoMovements() {
        assertThat(transactions.count()).isPositive();
    }

    @Test
    void runnerIsIdempotent_secondRunSeedsNothingMore() throws Exception {
        long cats = categories.count();
        long accs = accounts.count();
        long txs = transactions.count();

        seed.run(); // categories.count()!=0 and accounts.count()!=0 → both guards skip

        assertThat(categories.count()).isEqualTo(cats);
        assertThat(accounts.count()).isEqualTo(accs);
        assertThat(transactions.count()).isEqualTo(txs);
    }
}
