package com.omragul.processing.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates Flyway migrations V1 and V2 against a disposable PostgreSQL 18 instance.
 * Covers all 8 mandatory migration validation gate scenarios.
 */
class FlywayPostgreSqlMigrationTest {

    private static final String DEFAULT_PG_URL = System.getProperty("test.postgres.url",
            System.getenv().getOrDefault("TEST_POSTGRES_URL", "jdbc:postgresql://127.0.0.1:5439/processing_disposable_test"));
    private static final String DEFAULT_PG_USER = System.getProperty("test.postgres.user",
            System.getenv().getOrDefault("TEST_POSTGRES_USER", "postgres"));
    private static final String DEFAULT_PG_PASS = System.getProperty("test.postgres.password",
            System.getenv().getOrDefault("TEST_POSTGRES_PASSWORD", ""));

    private boolean isPostgreSqlAvailable() {
        try (Connection conn = DriverManager.getConnection(DEFAULT_PG_URL, DEFAULT_PG_USER, DEFAULT_PG_PASS)) {
            return conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    @BeforeEach
    void verifyPostgresEnvironment() {
        Assumptions.assumeTrue(isPostgreSqlAvailable(),
                "Real PostgreSQL container/service is unavailable in the current test environment.");
    }

    private Flyway buildFlyway(String targetVersion) {
        return Flyway.configure()
                .dataSource(DEFAULT_PG_URL, DEFAULT_PG_USER, DEFAULT_PG_PASS)
                .locations("classpath:db/migration")
                .target(targetVersion)
                .cleanDisabled(false)
                .load();
    }

    @Test
    @DisplayName("Scenario 1: Clean V1 database successfully migrates through V2")
    void testScenario1_CleanV1DatabaseMigratesThroughV2() {
        Flyway flywayV1 = buildFlyway("1");
        flywayV1.clean();
        flywayV1.migrate();
        assertEquals("1", flywayV1.info().current().getVersion().getVersion());

        Flyway flywayV2 = buildFlyway("2");
        assertDoesNotThrow(flywayV2::migrate, "Clean database must successfully apply V2 migration");
        assertEquals("2", flywayV2.info().current().getVersion().getVersion());
    }

    @Test
    @DisplayName("Scenario 2: Duplicate non-null (document_id, idempotency_key) pairs cause V2 to fail with expected error")
    void testScenario2_DuplicateIdempotencyPairsCauseV2ToFail() throws Exception {
        Flyway flywayV1 = buildFlyway("1");
        flywayV1.clean();
        flywayV1.migrate();

        UUID docId = UUID.randomUUID();
        String duplicateKey = "duplicate-token-123";

        // Insert duplicate pair directly into V1 schema
        try (Connection conn = DriverManager.getConnection(DEFAULT_PG_URL, DEFAULT_PG_USER, DEFAULT_PG_PASS)) {
            insertJob(conn, UUID.randomUUID(), docId, duplicateKey);
            insertJob(conn, UUID.randomUUID(), docId, duplicateKey);
        }

        Flyway flywayV2 = buildFlyway("2");
        FlywayException ex = assertThrows(FlywayException.class, flywayV2::migrate,
                "V2 migration must fail when duplicate (document_id, idempotency_key) pairs exist");

        assertTrue(ex.getMessage().contains("Flyway Migration V2 Precondition Failed"),
                "Error message must clearly identify precondition failure: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("Found 1 duplicate"),
                "Error message must report the duplicate count: " + ex.getMessage());
    }

    @Test
    @DisplayName("Scenario 3: After failure, verify historical rows and original keys remain unchanged")
    void testScenario3_HistoricalRowsAndKeysRemainUnchangedAfterFailure() throws Exception {
        Flyway flywayV1 = buildFlyway("1");
        flywayV1.clean();
        flywayV1.migrate();

        UUID docId = UUID.randomUUID();
        UUID jobId1 = UUID.randomUUID();
        UUID jobId2 = UUID.randomUUID();
        String originalKey = "original-exact-key-456";

        try (Connection conn = DriverManager.getConnection(DEFAULT_PG_URL, DEFAULT_PG_USER, DEFAULT_PG_PASS)) {
            insertJob(conn, jobId1, docId, originalKey);
            insertJob(conn, jobId2, docId, originalKey);
        }

        Flyway flywayV2 = buildFlyway("2");
        assertThrows(FlywayException.class, flywayV2::migrate);

        // Verify historical rows and keys remain 100% intact and unrenamed
        try (Connection conn = DriverManager.getConnection(DEFAULT_PG_URL, DEFAULT_PG_USER, DEFAULT_PG_PASS);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, idempotency_key FROM processing_jobs WHERE document_id = ? ORDER BY id ASC")) {
            ps.setObject(1, docId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "First duplicate job must exist");
                assertEquals(originalKey, rs.getString("idempotency_key"), "Original key must not be modified");
                assertTrue(rs.next(), "Second duplicate job must exist");
                assertEquals(originalKey, rs.getString("idempotency_key"), "Original key must not be modified");
                assertFalse(rs.next(), "No extra rows");
            }
        }
    }

    @Test
    @DisplayName("Scenario 4: Verify preceding migration DDL was rolled back on precondition failure")
    void testScenario4_PrecedingDdlRolledBackOnPreconditionFailure() throws Exception {
        Flyway flywayV1 = buildFlyway("1");
        flywayV1.clean();
        flywayV1.migrate();

        UUID docId = UUID.randomUUID();
        String duplicateKey = "dup-rollback-ddl-test";

        try (Connection conn = DriverManager.getConnection(DEFAULT_PG_URL, DEFAULT_PG_USER, DEFAULT_PG_PASS)) {
            insertJob(conn, UUID.randomUUID(), docId, duplicateKey);
            insertJob(conn, UUID.randomUUID(), docId, duplicateKey);
        }

        Flyway flywayV2 = buildFlyway("2");
        assertThrows(FlywayException.class, flywayV2::migrate);

        // In PostgreSQL transactional DDL, ALTER COLUMN was rolled back; current_step must still be VARCHAR(100)
        try (Connection conn = DriverManager.getConnection(DEFAULT_PG_URL, DEFAULT_PG_USER, DEFAULT_PG_PASS);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT character_maximum_length FROM information_schema.columns " +
                     "WHERE table_name = 'processing_jobs' AND column_name = 'current_step'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(100, rs.getInt(1),
                        "On failure, ALTER TABLE must be rolled back, keeping current_step at VARCHAR(100)");
            }
        }

        // Verify unique index was NOT created
        try (Connection conn = DriverManager.getConnection(DEFAULT_PG_URL, DEFAULT_PG_USER, DEFAULT_PG_PASS);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_processing_jobs_doc_idempotency_uniq'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Unique index must NOT exist after failed migration");
            }
        }
    }

    @Test
    @DisplayName("Scenario 5: Multiple NULL idempotency keys for one document remain allowed")
    void testScenario5_MultipleNullIdempotencyKeysAllowed() throws Exception {
        Flyway flywayV1 = buildFlyway("1");
        flywayV1.clean();
        flywayV1.migrate();

        UUID docId = UUID.randomUUID();
        try (Connection conn = DriverManager.getConnection(DEFAULT_PG_URL, DEFAULT_PG_USER, DEFAULT_PG_PASS)) {
            insertJob(conn, UUID.randomUUID(), docId, null);
            insertJob(conn, UUID.randomUUID(), docId, null);
            insertJob(conn, UUID.randomUUID(), docId, null);
        }

        Flyway flywayV2 = buildFlyway("2");
        assertDoesNotThrow(flywayV2::migrate, "Multiple NULL idempotency keys must migrate to V2 successfully");
        assertEquals("2", flywayV2.info().current().getVersion().getVersion());

        // Verify adding another NULL after V2 migration is also allowed
        try (Connection conn = DriverManager.getConnection(DEFAULT_PG_URL, DEFAULT_PG_USER, DEFAULT_PG_PASS)) {
            assertDoesNotThrow(() -> insertJob(conn, UUID.randomUUID(), docId, null),
                    "Inserting additional NULL idempotency key after migration must succeed");
        }
    }

    @Test
    @DisplayName("Scenario 6: Same non-null key across different documents remains allowed")
    void testScenario6_SameKeyAcrossDifferentDocumentsAllowed() throws Exception {
        Flyway flywayV1 = buildFlyway("1");
        flywayV1.clean();
        flywayV1.migrate();

        UUID docId1 = UUID.randomUUID();
        UUID docId2 = UUID.randomUUID();
        String sharedKey = "shared-across-docs-key";

        try (Connection conn = DriverManager.getConnection(DEFAULT_PG_URL, DEFAULT_PG_USER, DEFAULT_PG_PASS)) {
            insertJob(conn, UUID.randomUUID(), docId1, sharedKey);
            insertJob(conn, UUID.randomUUID(), docId2, sharedKey);
        }

        Flyway flywayV2 = buildFlyway("2");
        assertDoesNotThrow(flywayV2::migrate, "Same key on distinct document_ids must migrate to V2 successfully");
        assertEquals("2", flywayV2.info().current().getVersion().getVersion());

        // Verify inserting third doc with same key after migration also succeeds
        UUID docId3 = UUID.randomUUID();
        try (Connection conn = DriverManager.getConnection(DEFAULT_PG_URL, DEFAULT_PG_USER, DEFAULT_PG_PASS)) {
            assertDoesNotThrow(() -> insertJob(conn, UUID.randomUUID(), docId3, sharedKey),
                    "Same idempotency key with distinct document_id must succeed post-migration");
        }
    }

    @Test
    @DisplayName("Scenario 7: Unique pairs are rejected when duplicated after migration")
    void testScenario7_DuplicateRejectedPostMigration() throws Exception {
        Flyway flyway = buildFlyway("2");
        flyway.clean();
        flyway.migrate();

        UUID docId = UUID.randomUUID();
        String idemKey = "post-migration-unique-key";

        try (Connection conn = DriverManager.getConnection(DEFAULT_PG_URL, DEFAULT_PG_USER, DEFAULT_PG_PASS)) {
            // First insert succeeds
            insertJob(conn, UUID.randomUUID(), docId, idemKey);

            // Second insert with same (docId, idemKey) must violate unique constraint
            SQLException ex = assertThrows(SQLException.class, () -> insertJob(conn, UUID.randomUUID(), docId, idemKey),
                    "Duplicate insert must violate PostgreSQL unique index");
            assertEquals("23505", ex.getSQLState(), "SQLState must be 23505 (unique_violation)");
        }
    }

    @Test
    @DisplayName("Scenario 8: Verify actual index definition and column length in PostgreSQL system catalogs")
    void testScenario8_VerifyIndexDefinitionAndColumnLengthInPostgresCatalogs() throws Exception {
        Flyway flyway = buildFlyway("2");
        flyway.clean();
        flyway.migrate();

        try (Connection conn = DriverManager.getConnection(DEFAULT_PG_URL, DEFAULT_PG_USER, DEFAULT_PG_PASS)) {
            // 1. Verify column length from information_schema
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT character_maximum_length FROM information_schema.columns " +
                    "WHERE table_name = 'processing_jobs' AND column_name = 'current_step'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Column current_step must exist");
                    assertEquals(500, rs.getInt(1), "current_step column length in PostgreSQL catalog must be 500");
                }
            }

            // 2. Verify unique index in pg_indexes
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT indexname, indexdef FROM pg_indexes " +
                    "WHERE tablename = 'processing_jobs' AND indexname = 'idx_processing_jobs_doc_idempotency_uniq'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Index idx_processing_jobs_doc_idempotency_uniq must exist in pg_indexes");
                    String indexDef = rs.getString("indexdef");
                    assertTrue(indexDef.contains("UNIQUE INDEX"), "Index must be declared UNIQUE: " + indexDef);
                    assertTrue(indexDef.contains("document_id, idempotency_key"),
                            "Index must cover (document_id, idempotency_key): " + indexDef);
                }
            }

            // 3. Verify single-column lookup index in pg_indexes
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT indexname FROM pg_indexes " +
                    "WHERE tablename = 'processing_jobs' AND indexname = 'idx_processing_jobs_idempotency_key'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Secondary index idx_processing_jobs_idempotency_key must exist");
                }
            }
        }
    }

    @Test
    @DisplayName("Scenario 9: Migration through V3 successfully adds owner_id column and index to PostgreSQL")
    void testScenario9_V3MigrationAddsOwnerIdColumnAndIndex() throws Exception {
        Flyway flyway = buildFlyway("3");
        flyway.clean();
        flyway.migrate();
        assertEquals("3", flyway.info().current().getVersion().getVersion());

        try (Connection conn = DriverManager.getConnection(DEFAULT_PG_URL, DEFAULT_PG_USER, DEFAULT_PG_PASS)) {
            // Verify owner_id column in information_schema
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT data_type, is_nullable FROM information_schema.columns " +
                    "WHERE table_name = 'processing_jobs' AND column_name = 'owner_id'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Column owner_id must exist in processing_jobs");
                    assertEquals("uuid", rs.getString("data_type").toLowerCase());
                    assertEquals("YES", rs.getString("is_nullable"), "owner_id must be nullable for historical compatibility");
                }
            }

            // Verify index idx_processing_jobs_owner_id
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT indexname FROM pg_indexes " +
                    "WHERE tablename = 'processing_jobs' AND indexname = 'idx_processing_jobs_owner_id'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Index idx_processing_jobs_owner_id must exist");
                }
            }
        }
    }

    private void insertJob(Connection conn, UUID jobId, UUID docId, String idempotencyKey) throws SQLException {
        String sql = "INSERT INTO processing_jobs (id, document_id, status, stage, current_step, attempt_count, max_attempts, " +
                     "idempotency_key, created_at, updated_at, version) " +
                     "VALUES (?, ?, 'QUEUED', 'INITIALIZATION', 'Initial step', 0, 3, ?, ?, ?, 0)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, jobId);
            ps.setObject(2, docId);
            ps.setString(3, idempotencyKey);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }
}
