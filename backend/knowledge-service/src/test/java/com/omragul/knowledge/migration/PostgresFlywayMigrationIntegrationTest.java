package com.omragul.knowledge.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

class PostgresFlywayMigrationIntegrationTest {

    @Test
    @DisplayName("Verify all Flyway migrations V1..V11 execute cleanly against clean PostgreSQL 17")
    void testFlywayMigrationsAgainstPostgres17() {
        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            dockerAvailable = false;
        }

        Assumptions.assumeTrue(
                dockerAvailable,
                "Docker is not running on this host; standalone PostgreSQL migration test must be run when Docker daemon is started"
        );

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("test_knowledge_db")
                .withUsername("test_user")
                .withPassword("test_pass")) {

            postgres.start();

            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load();

            MigrateResult result = flyway.migrate();

            assertTrue(result.success, "Flyway migrations must succeed");
            assertEquals(11, result.migrationsExecuted, "Expected exactly 11 Flyway migrations to be executed");
        }
    }
}
