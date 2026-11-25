package com.omragul.processing.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
class IdempotencyDuplicateDetectionQueryTest {

    @Autowired
    private DataSource dataSource;

    private static final String CREATE_V1_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS v1_legacy_jobs (" +
            "    id UUID PRIMARY KEY," +
            "    document_id UUID NOT NULL," +
            "    status VARCHAR(30) NOT NULL," +
            "    stage VARCHAR(30) NOT NULL," +
            "    current_step VARCHAR(100)," +
            "    attempt_count INT NOT NULL DEFAULT 0," +
            "    max_attempts INT NOT NULL DEFAULT 3," +
            "    idempotency_key VARCHAR(255)," +
            "    created_at TIMESTAMP WITH TIME ZONE NOT NULL," +
            "    updated_at TIMESTAMP WITH TIME ZONE NOT NULL," +
            "    version BIGINT NOT NULL DEFAULT 0" +
            ")";

    private static final String PRECONDITION_QUERY =
            "SELECT COUNT(*) FROM (" +
            "    SELECT document_id, idempotency_key " +
            "    FROM v1_legacy_jobs " +
            "    WHERE idempotency_key IS NOT NULL " +
            "    GROUP BY document_id, idempotency_key " +
            "    HAVING COUNT(*) > 1" +
            ") duplicates";

    private static final String INSERT_JOB_SQL =
            "INSERT INTO v1_legacy_jobs (" +
            "    id, document_id, status, stage, current_step, attempt_count, max_attempts, " +
            "    idempotency_key, created_at, updated_at, version" +
            ") VALUES (?, ?, 'QUEUED', 'INITIALIZATION', 'Step', 0, 3, ?, ?, ?, 0)";

    @BeforeEach
    void setupV1Table() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_V1_TABLE_SQL);
            stmt.execute("DELETE FROM v1_legacy_jobs");
        }
    }

    private int executePreconditionCheck() throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(PRECONDITION_QUERY);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void insertJob(UUID jobId, UUID docId, String idemKey) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_JOB_SQL)) {
            ps.setObject(1, jobId);
            ps.setObject(2, docId);
            ps.setString(3, idemKey);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("Precondition check: Returns 0 on clean database with unique jobs")
    void testCleanDatabaseReturnsZeroDuplicates() throws Exception {
        UUID docId1 = UUID.randomUUID();
        UUID docId2 = UUID.randomUUID();
        insertJob(UUID.randomUUID(), docId1, "key-doc1");
        insertJob(UUID.randomUUID(), docId2, "key-doc2");

        assertEquals(0, executePreconditionCheck(), "Clean database must have 0 duplicate pairs");
    }

    @Test
    @DisplayName("Precondition check: Detects duplicate non-null (document_id, idempotency_key) pair")
    void testDetectsDuplicateNonNullPair() throws Exception {
        UUID docId = UUID.randomUUID();
        String sharedKey = "dup-key-same-doc";

        // Insert first job
        insertJob(UUID.randomUUID(), docId, sharedKey);
        assertEquals(0, executePreconditionCheck());

        // Insert duplicate pair (allowed in V1 schema without unique constraint)
        insertJob(UUID.randomUUID(), docId, sharedKey);
        assertEquals(1, executePreconditionCheck(), "Must detect exactly 1 duplicate (document_id, idempotency_key) pair");
    }

    @Test
    @DisplayName("Precondition check: Multiple NULL idempotency keys for same document are NOT counted as duplicates")
    void testMultipleNullIdempotencyKeysNotCounted() throws Exception {
        UUID docId = UUID.randomUUID();

        insertJob(UUID.randomUUID(), docId, null);
        insertJob(UUID.randomUUID(), docId, null);
        insertJob(UUID.randomUUID(), docId, null);

        assertEquals(0, executePreconditionCheck(), "Multiple NULL idempotency keys must not trigger precondition failure");
    }

    @Test
    @DisplayName("Precondition check: Same idempotency key across different documents is NOT counted as duplicate")
    void testSameKeyAcrossDifferentDocumentsNotCounted() throws Exception {
        UUID docId1 = UUID.randomUUID();
        UUID docId2 = UUID.randomUUID();
        String sharedKey = "shared-batch-token";

        insertJob(UUID.randomUUID(), docId1, sharedKey);
        insertJob(UUID.randomUUID(), docId2, sharedKey);

        assertEquals(0, executePreconditionCheck(), "Same idempotency key with distinct document IDs must not be counted as duplicate");
    }
}
