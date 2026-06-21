package com.xroig.finance;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for Level-2 repository tests ({@code @DataJpaTest} against a real
 * PostgreSQL via Testcontainers). The container is {@code static} so it is
 * started once and shared across every subclass (and every test method) instead
 * of one container per class.
 *
 * <p>{@code @ServiceConnection} wires the JPA datasource to the container, and
 * {@code replace = NONE} keeps Spring from swapping in an embedded database, so
 * Flyway runs the real migrations and Hibernate's {@code ddl-auto=validate}
 * checks the entities against that schema. Each test method runs in a
 * transaction that rolls back, keeping them isolated.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
public abstract class PostgresTestBase {

    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
}
