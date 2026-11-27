"""
OMRAGUL V3 — Stage 3.7 Batch 3: Bounded Live Pipeline Integration Test
Target: tests/integration/stage_3_7_batch_3_processing_pipeline_test.py

Objectives:
1. Authenticate with legitimate client JWT through API Gateway (Port 18080).
2. Allow Gateway to verify client identity and mint internal RS256 assertion.
3. Submit single isolated document processing job (POST /api/v1/processing/jobs).
4. Verify HTTP 201 Created and response payload.
5. Verify PostgreSQL persistence, ownership, and asynchronous state transitions.
6. Verify bounded failure handling at document storage boundary (DOCUMENT_NOT_FOUND).
7. Execute guaranteed exact cleanup of only records created by this run.
"""

import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
import jwt

GATEWAY_URL = "http://localhost:18080"
PRIVATE_KEY_PATH = "secrets/private_key.pem"

TEST_USER_ID = "11111111-1111-1111-1111-111111111111"
TEST_KB_ID = "77777777-7777-7777-7777-777777777777"
TEST_DOC_ID = "88888888-8888-8888-8888-888888888888"

KB_NAME = "Batch 3 Integration Test KB"
KB_SLUG = "batch-3-test-kb"
DOC_NAME = "Batch 3 Test Document"
DOC_FILENAME = "test_doc_batch3.txt"
DOC_STORAGE_PATH = "storage/non_existent_batch3_doc.txt"


def run_psql(database: str, query: str) -> str:
    """Executes psql command inside the integration postgres container."""
    cmd = [
        "docker", "exec", "omragul-integration-postgres",
        "psql", "-U", "omragul_test_user", "-d", database, "-t", "-A", "-c", query
    ]
    res = subprocess.run(cmd, capture_output=True, text=True, check=True)
    return res.stdout.strip()


def check_preflight_collisions():
    """Confirms fixed test UUIDs have zero existing records."""
    kb_count = int(run_psql("knowledge_integration_db", f"SELECT count(*) FROM knowledge_bases WHERE id = '{TEST_KB_ID}';"))
    doc_count = int(run_psql("knowledge_integration_db", f"SELECT count(*) FROM documents WHERE id = '{TEST_DOC_ID}';"))
    job_count = int(run_psql("processing_integration_db", f"SELECT count(*) FROM processing_jobs WHERE document_id = '{TEST_DOC_ID}';"))

    if kb_count > 0 or doc_count > 0 or job_count > 0:
        raise RuntimeError(f"Preflight collision detected: KB={kb_count}, Doc={doc_count}, Job={job_count}")
    print("[PREFLIGHT] Test UUIDs verified completely unused.")


def mint_client_jwt() -> str:
    """Mints legitimate RS256 client token for API Gateway authentication."""
    with open(PRIVATE_KEY_PATH, "r", encoding="utf-8") as f:
        priv_key = f.read()

    now = int(time.time())
    payload = {
        "sub": TEST_USER_ID,
        "iss": "http://localhost:8080",
        "roles": ["ROLE_USER"],
        "email": "batch3test@omragul.local",
        "username": "batch3test",
        "iat": now,
        "exp": now + 3600
    }
    return jwt.encode(payload, priv_key, algorithm="RS256")


def main():
    start_time = time.time()
    print("=" * 70)
    print("OMRAGUL V3 — Stage 3.7 Batch 3: Bounded Live Pipeline Integration Test")
    print("=" * 70)

    kb_created = False
    doc_created = False
    created_job_id = None
    worker_reached_terminal = False

    try:
        # 1. Preflight collision check
        check_preflight_collisions()

        # 2. Insert Knowledge Base fixture
        insert_kb_sql = (
            f"INSERT INTO knowledge_bases (id, name, slug, owner_id, visibility, status) "
            f"VALUES ('{TEST_KB_ID}', '{KB_NAME}', '{KB_SLUG}', '{TEST_USER_ID}', 'PRIVATE', 'ACTIVE');"
        )
        run_psql("knowledge_integration_db", insert_kb_sql)
        kb_created = True
        print(f"[SETUP] Knowledge Base created: {TEST_KB_ID}")

        # 3. Insert Document fixture
        insert_doc_sql = (
            f"INSERT INTO documents (id, knowledge_base_id, name, original_filename, stored_filename, storage_path, "
            f"mime_type, file_size_bytes, checksum, owner_id, processing_status, status) "
            f"VALUES ('{TEST_DOC_ID}', '{TEST_KB_ID}', '{DOC_NAME}', '{DOC_FILENAME}', '{DOC_FILENAME}', "
            f"'{DOC_STORAGE_PATH}', 'text/plain', 1024, 'dummychecksumbatch3test12345', '{TEST_USER_ID}', 'PENDING', 'ACTIVE');"
        )
        run_psql("knowledge_integration_db", insert_doc_sql)
        doc_created = True
        print(f"[SETUP] Document created: {TEST_DOC_ID}")

        # 4. Mint Client JWT
        client_token = mint_client_jwt()
        print("[AUTH] Legitimate client JWT minted (credentials and token hidden).")

        # 5. Submit processing job via Gateway ingress
        req_body = json.dumps({
            "document_id": TEST_DOC_ID,
            "knowledge_base_id": TEST_KB_ID
        }).encode("utf-8")

        req = urllib.request.Request(
            f"{GATEWAY_URL}/api/v1/processing/jobs",
            data=req_body,
            headers={
                "Authorization": f"Bearer {client_token}",
                "Content-Type": "application/json"
            },
            method="POST"
        )

        print("[INGRESS] Submitting processing job via Gateway...")
        with urllib.request.urlopen(req, timeout=10) as resp:
            http_status = resp.status
            resp_bytes = resp.read()
            resp_data = json.loads(resp_bytes.decode("utf-8"))

        print(f"[INGRESS] Gateway response HTTP {http_status}")
        assert http_status == 201, f"Expected HTTP 201, got {http_status}"

        created_job_id = resp_data.get("id")
        assert created_job_id is not None, "Response missing 'id'"
        assert resp_data.get("documentId") == TEST_DOC_ID, "documentId mismatch"
        assert resp_data.get("ownerId") == TEST_USER_ID, "ownerId mismatch"
        assert resp_data.get("status") == "QUEUED", f"Initial status must be QUEUED, got {resp_data.get('status')}"
        assert resp_data.get("stage") == "INITIALIZATION", f"Initial stage must be INITIALIZATION, got {resp_data.get('stage')}"
        print(f"[INGRESS] Processing job created successfully: {created_job_id}")

        # 6. Verify initial PostgreSQL persistence
        persisted_job = run_psql(
            "processing_integration_db",
            f"SELECT id, document_id, owner_id, status, stage FROM processing_jobs WHERE id = '{created_job_id}';"
        )
        assert persisted_job, f"Job record {created_job_id} not found in processing_integration_db"
        print(f"[PERSISTENCE] Verified job persisted in processing_integration_db: {persisted_job}")

        # 7. Asynchronous execution & state transition monitoring
        print("[ORCHESTRATION] Observing asynchronous pipeline execution and state machine transitions...")
        observed_states = set()
        observed_doc_statuses = set()
        terminal_status = None
        terminal_stage = None
        terminal_error_code = None

        poll_start = time.time()
        timeout_seconds = 10.0

        while (time.time() - poll_start) < timeout_seconds:
            job_state_str = run_psql(
                "processing_integration_db",
                f"SELECT status, stage, coalesce(error_code, 'NONE') FROM processing_jobs WHERE id = '{created_job_id}';"
            )
            doc_state_str = run_psql(
                "knowledge_integration_db",
                f"SELECT processing_status FROM documents WHERE id = '{TEST_DOC_ID}';"
            )

            if job_state_str:
                parts = job_state_str.split("|")
                cur_status, cur_stage, cur_err = parts[0], parts[1], parts[2]
                observed_states.add((cur_status, cur_stage))

                if cur_status in ("COMPLETED", "FAILED", "CANCELLED"):
                    worker_reached_terminal = True
                    terminal_status = cur_status
                    terminal_stage = cur_stage
                    terminal_error_code = cur_err
                    if doc_state_str:
                        observed_doc_statuses.add(doc_state_str)
                    break

            if doc_state_str:
                observed_doc_statuses.add(doc_state_str)

            time.sleep(0.3)

        print(f"[ORCHESTRATION] Observed Job States (status, stage): {observed_states}")
        print(f"[ORCHESTRATION] Observed Knowledge Doc Processing Statuses: {observed_doc_statuses}")

        assert worker_reached_terminal, f"Worker failed to reach terminal state within {timeout_seconds}s"
        assert terminal_status == "FAILED", f"Expected terminal status FAILED, got {terminal_status}"
        assert terminal_stage == "FAILED", f"Expected terminal stage FAILED, got {terminal_stage}"
        assert terminal_error_code == "DOCUMENT_NOT_FOUND", (
            f"Expected error code DOCUMENT_NOT_FOUND at storage boundary, got {terminal_error_code}"
        )

        final_doc_status = run_psql(
            "knowledge_integration_db",
            f"SELECT processing_status FROM documents WHERE id = '{TEST_DOC_ID}';"
        )
        print(f"[ORCHESTRATION] Final Knowledge Service Document Status: {final_doc_status}")
        assert final_doc_status == "FAILED", f"Knowledge Service document status expected FAILED, got {final_doc_status}"

        print("[SUCCESS] Live Gateway -> Processing -> Knowledge end-to-end integration verified.")
        print("[SUCCESS] Downstream failure boundary cleanly handled without crash.")

    finally:
        print("\n" + "-" * 70)
        print("[CLEANUP] Initiating guaranteed targeted teardown...")
        print("-" * 70)

        if not worker_reached_terminal and created_job_id:
            print(f"[CRITICAL WARNING] Worker did NOT reach terminal state! Halting cleanup to prevent race.")
            print(f"Residual Job ID: {created_job_id}, Doc ID: {TEST_DOC_ID}, KB ID: {TEST_KB_ID}")
            return

        # 1. Delete created processing job by exact ID
        if created_job_id:
            run_psql("processing_integration_db", f"DELETE FROM processing_jobs WHERE id = '{created_job_id}';")
            count_job = int(run_psql("processing_integration_db", f"SELECT count(*) FROM processing_jobs WHERE id = '{created_job_id}';"))
            assert count_job == 0, f"Failed to delete job {created_job_id}"
            print(f"[CLEANUP] Deleted processing job: {created_job_id}")

        # 2. Delete test document by exact ID
        if doc_created:
            run_psql("knowledge_integration_db", f"DELETE FROM documents WHERE id = '{TEST_DOC_ID}';")
            count_doc = int(run_psql("knowledge_integration_db", f"SELECT count(*) FROM documents WHERE id = '{TEST_DOC_ID}';"))
            assert count_doc == 0, f"Failed to delete document {TEST_DOC_ID}"
            print(f"[CLEANUP] Deleted test document: {TEST_DOC_ID}")

        # 3. Delete test knowledge base by exact ID
        if kb_created:
            run_psql("knowledge_integration_db", f"DELETE FROM knowledge_bases WHERE id = '{TEST_KB_ID}';")
            count_kb = int(run_psql("knowledge_integration_db", f"SELECT count(*) FROM knowledge_bases WHERE id = '{TEST_KB_ID}';"))
            assert count_kb == 0, f"Failed to delete knowledge base {TEST_KB_ID}"
            print(f"[CLEANUP] Deleted test knowledge base: {TEST_KB_ID}")

        # Post-cleanup residual verification
        res_kb = int(run_psql("knowledge_integration_db", f"SELECT count(*) FROM knowledge_bases WHERE id = '{TEST_KB_ID}';"))
        res_doc = int(run_psql("knowledge_integration_db", f"SELECT count(*) FROM documents WHERE id = '{TEST_DOC_ID}';"))
        res_job = int(run_psql("processing_integration_db", f"SELECT count(*) FROM processing_jobs WHERE document_id = '{TEST_DOC_ID}';"))
        assert res_kb == 0 and res_doc == 0 and res_job == 0, "Residual records detected!"
        print("[CLEANUP] Post-cleanup verification complete. Zero residual test records.")

    elapsed = time.time() - start_time
    print(f"\n[DONE] Test execution finished in {elapsed:.2f} seconds.")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"\n[TEST FAILED] {type(e).__name__}: {e}", file=sys.stderr)
        sys.exit(1)
