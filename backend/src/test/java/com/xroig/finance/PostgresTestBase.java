package com.xroig.finance;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for Level-2 repository tests ({@code @DataJpaTest} against a real
 * PostgreSQL via Testcontainers).
 *
 * <p>Uses the <b>singleton container</b> pattern: the container is started once
 * in a static initializer and never stopped (Ryuk reaps it when the JVM exits),
 * so a single instance — on a single, stable port — is shared by every test
 * class. This is deliberately <em>not</em> the {@code @Container}/
 * {@code @Testcontainers} lifecycle: that stops the container in the first
 * class's {@code afterAll}, leaving the cached Spring context (reused across
 * classes) pointing at a dead port and every later test failing with a
 * connection timeout.
 *
 * <p>{@code replace = NONE} keeps Spring from swapping in an embedded database,
 * so Flyway runs the real migrations and Hibernate's {@code ddl-auto=validate}
 * checks the entities against that schema. Each test method runs in a
 * transaction that rolls back, keeping them isolated.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class PostgresTestBase {

    protected static final PostgreSQLContainer<?> POSTGRES =
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
}
