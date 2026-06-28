package com.xroig.finance;

import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity check for the Testcontainers infra: the container starts, Flyway builds the
 * schema and Hibernate validates the entities against it, so a repository query runs
 * without error.
 */
class SchemaSmokeTest extends PostgresTestBase {

    @Autowired
    private AccountJpaRepository accountRepository;

    @Test
    void containerIsRunningAndSchemaIsQueryable() {
        assertThat(POSTGRES.isRunning()).isTrue();
        // No exception here means Flyway created the schema and ddl-auto=validate passed.
        assertThat(accountRepository.count()).isGreaterThanOrEqualTo(0);
    }
}
