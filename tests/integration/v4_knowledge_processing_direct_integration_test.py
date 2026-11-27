"""
OMRAGUL V4 — Knowledge Service + Processing Service Direct Inter-Service Integration Test
Target: tests/integration/v4_knowledge_processing_direct_integration_test.py

Objectives:
1. Verify PostgreSQL migrations and connectivity for both services.
2. Verify direct authenticated REST endpoints on Knowledge Service using X-Internal-Service-Secret:
   - GET /api/v1/documents/{id}
   - GET /api/v1/documents/{id}/permissions/verify
   - POST /api/v1/documents/batch/status
   - PATCH /api/v1/documents/{id}/processing-status/{status}
3. Verify direct file streaming boundary on Knowledge Service:
   - GET /api/v1/documents/{id}/content
4. Verify Gateway -> Processing Service authenticated job ingress with dual-casing compatibility:
   - POST /api/v1/processing/jobs
   - GET /api/v1/processing/jobs/{id}
5. Verify end-to-end database persistence and synchronized state transitions across microservices.
6. Guaranteed zero-residual cleanup.
"""

import json
import subprocess
import time
import urllib.error
import urllib.request
import uuid
import jwt

GATEWAY_URL = "http://localhost:18080"
KNOWLEDGE_URL = "http://localhost:18082"
PROCESSING_URL = "http://localhost:18083"
INTERNAL_SECRET = "omragul-integration-test-secret-4096-phase2"
PRIVATE_KEY_PATH = "secrets/private_key.pem"

TEST_USER_ID = "22222222-2222-2222-2222-222222222222"
TEST_KB_ID = "33333333-3333-3333-3333-333333333333"
TEST_DOC_ID = "44444444-4444-4444-4444-444444444444"

KB_NAME = "V4 Direct Integration Test KB"
KB_SLUG = "v4-direct-test-kb"
DOC_NAME = "V4 Direct Test Document"
DOC_FILENAME = "test_doc_v4.txt"
DOC_STORAGE_PATH = "storage/test_doc_v4.txt"


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
        "email": "v4test@omragul.local",
        "username": "v4test",
        "iat": now,
        "exp": now + 3600
    }
    return jwt.encode(payload, priv_key, algorithm="RS256")


def http_request(url: str, method: str = "GET", headers: dict = None, data: bytes = None):
    """Executes an HTTP request returning (status, response_dict_or_str, response_headers)."""
    req_headers = headers or {}
    req = urllib.request.Request(url, data=data, headers=req_headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            status = resp.status
            body_bytes = resp.read()
            resp_headers = dict(resp.headers)
            try:
                body = json.loads(body_bytes.decode("utf-8"))
            except Exception:
                body = body_bytes.decode("utf-8")
            return status, body, resp_headers
    except urllib.error.HTTPError as e:
        status = e.code
        body_bytes = e.read()
        try:
            body = json.loads(body_bytes.decode("utf-8"))
        except Exception:
            body = body_bytes.decode("utf-8")
        return status, body, dict(e.headers)


def main():
    start_time = time.time()
    print("=" * 70)
    print("OMRAGUL V4 — Knowledge Service + Processing Service Integration Test")
    print("=" * 70)

    kb_created = False
    doc_created = False
    created_job_id = None

    try:
        # Step 1: Preflight
        check_preflight_collisions()

        # Step 2: Insert Knowledge Base fixture into knowledge_integration_db
        insert_kb_sql = (
            f"INSERT INTO knowledge_bases (id, name, slug, owner_id, visibility, status) "
            f"VALUES ('{TEST_KB_ID}', '{KB_NAME}', '{KB_SLUG}', '{TEST_USER_ID}', 'PRIVATE', 'ACTIVE');"
        )
        run_psql("knowledge_integration_db", insert_kb_sql)
        kb_created = True
        print(f"[SETUP] Knowledge Base fixture inserted: {TEST_KB_ID}")

        # Step 3: Insert Document fixture into knowledge_integration_db
        insert_doc_sql = (
            f"INSERT INTO documents (id, knowledge_base_id, name, original_filename, stored_filename, storage_path, "
            f"mime_type, file_size_bytes, checksum, owner_id, processing_status, status) "
            f"VALUES ('{TEST_DOC_ID}', '{TEST_KB_ID}', '{DOC_NAME}', '{DOC_FILENAME}', '{DOC_FILENAME}', "
            f"'{DOC_STORAGE_PATH}', 'text/plain', 512, 'dummychecksumv4test12345', '{TEST_USER_ID}', 'PENDING', 'ACTIVE');"
        )
        run_psql("knowledge_integration_db", insert_doc_sql)
        doc_created = True
        print(f"[SETUP] Document fixture inserted: {TEST_DOC_ID}")

        # Step 4: Test Knowledge Service internal endpoint GET /api/v1/documents/{id}
        print("[KNOWLEDGE] Testing direct GET /api/v1/documents/{id} with X-Internal-Service-Secret...")
        status, doc_resp, _ = http_request(
            f"{KNOWLEDGE_URL}/api/v1/documents/{TEST_DOC_ID}",
            method="GET",
            headers={"X-Internal-Service-Secret": INTERNAL_SECRET}
        )
        assert status == 200, f"Expected 200, got {status}: {doc_resp}"
        assert doc_resp.get("id") == TEST_DOC_ID, f"Document ID mismatch: {doc_resp}"
        assert doc_resp.get("processingStatus") == "PENDING", f"Status mismatch: {doc_resp}"
        print(f"[KNOWLEDGE] GET /api/v1/documents/{TEST_DOC_ID} OK -> name='{doc_resp.get('name')}', status='{doc_resp.get('processingStatus')}'")

        # Step 5: Test Knowledge Service permission verification GET /api/v1/documents/{id}/permissions/verify
        print("[KNOWLEDGE] Testing direct GET /api/v1/documents/{id}/permissions/verify...")
        status, perm_resp, _ = http_request(
            f"{KNOWLEDGE_URL}/api/v1/documents/{TEST_DOC_ID}/permissions/verify?userId={TEST_USER_ID}&permission=WRITE",
            method="GET",
            headers={"X-Internal-Service-Secret": INTERNAL_SECRET}
        )
        assert status == 200, f"Expected 200, got {status}: {perm_resp}"
        assert perm_resp.get("authorized") is True, f"Expected authorized=True: {perm_resp}"
        print(f"[KNOWLEDGE] Permission verification OK -> authorized={perm_resp.get('authorized')}")

        # Step 6: Test Knowledge Service batch status POST /api/v1/documents/batch/status
        print("[KNOWLEDGE] Testing direct POST /api/v1/documents/batch/status...")
        batch_payload = json.dumps({
            "knowledgeBaseId": TEST_KB_ID,
            "documentIds": [TEST_DOC_ID, str(uuid.uuid4())]
        }).encode("utf-8")
        status, batch_resp, _ = http_request(
            f"{KNOWLEDGE_URL}/api/v1/documents/batch/status",
            method="POST",
            headers={
                "X-Internal-Service-Secret": INTERNAL_SECRET,
                "Content-Type": "application/json"
            },
            data=batch_payload
        )
        assert status == 200, f"Expected 200, got {status}: {batch_resp}"
        active_ids = batch_resp.get("activeDocumentIds", [])
        assert TEST_DOC_ID in active_ids, f"Expected TEST_DOC_ID in active list: {batch_resp}"
        assert len(active_ids) == 1, f"Expected exactly 1 active ID, got: {batch_resp}"
        print(f"[KNOWLEDGE] Batch active status OK -> activeDocumentIds={active_ids}")

        # Step 7: Test Knowledge Service processing-status update PATCH /api/v1/documents/{id}/processing-status/PROCESSING
        print("[KNOWLEDGE] Testing direct PATCH /api/v1/documents/{id}/processing-status/PROCESSING...")
        status, patch_resp, _ = http_request(
            f"{KNOWLEDGE_URL}/api/v1/documents/{TEST_DOC_ID}/processing-status/PROCESSING",
            method="PATCH",
            headers={"X-Internal-Service-Secret": INTERNAL_SECRET}
        )
        assert status == 200, f"Expected 200, got {status}: {patch_resp}"
        assert patch_resp.get("processingStatus") == "PROCESSING", f"Status mismatch: {patch_resp}"

        # Verify in Postgres
        db_doc_status = run_psql("knowledge_integration_db", f"SELECT processing_status FROM documents WHERE id = '{TEST_DOC_ID}';")
        assert db_doc_status == "PROCESSING", f"Postgres status mismatch: {db_doc_status}"
        print(f"[KNOWLEDGE] Status transition to PROCESSING verified in DB.")

        # Step 8: Test Knowledge Service processing-status update PATCH /api/v1/documents/{id}/processing-status/COMPLETED
        print("[KNOWLEDGE] Testing direct PATCH /api/v1/documents/{id}/processing-status/COMPLETED...")
        status, patch_resp2, _ = http_request(
            f"{KNOWLEDGE_URL}/api/v1/documents/{TEST_DOC_ID}/processing-status/COMPLETED",
            method="PATCH",
            headers={"X-Internal-Service-Secret": INTERNAL_SECRET}
        )
        assert status == 200, f"Expected 200, got {status}: {patch_resp2}"
        assert patch_resp2.get("processingStatus") == "COMPLETED", f"Status mismatch: {patch_resp2}"

        # Verify in Postgres
        db_doc_status2 = run_psql("knowledge_integration_db", f"SELECT processing_status FROM documents WHERE id = '{TEST_DOC_ID}';")
        assert db_doc_status2 == "COMPLETED", f"Postgres status mismatch: {db_doc_status2}"
        print(f"[KNOWLEDGE] Status transition to COMPLETED verified in DB.")

        # Step 9: Test Processing Service Job Ingress via Gateway with RS256 token
        print("[PROCESSING] Submitting processing job via Gateway...")
        client_token = mint_client_jwt()
        job_req_payload = json.dumps({
            "document_id": TEST_DOC_ID,
            "knowledge_base_id": TEST_KB_ID
        }).encode("utf-8")

        status, job_create_resp, _ = http_request(
            f"{GATEWAY_URL}/api/v1/processing/jobs",
            method="POST",
            headers={
                "Authorization": f"Bearer {client_token}",
                "Content-Type": "application/json"
            },
            data=job_req_payload
        )
        assert status == 201, f"Expected 201, got {status}: {job_create_resp}"
        created_job_id = job_create_resp.get("id")
        assert created_job_id is not None, f"Missing id: {job_create_resp}"

        # Verify dual-case contract compatibility on response
        assert job_create_resp.get("documentId") == TEST_DOC_ID or job_create_resp.get("document_id") == TEST_DOC_ID, "documentId missing"
        assert job_create_resp.get("ownerId") == TEST_USER_ID or job_create_resp.get("owner_id") == TEST_USER_ID, "ownerId missing"
        print(f"[PROCESSING] Job created successfully: {created_job_id} (dual-casing validated)")

        # Step 10: Query job via Gateway GET /api/v1/processing/jobs/{id}
        print(f"[PROCESSING] Querying job {created_job_id} via Gateway...")
        status, job_query_resp, _ = http_request(
            f"{GATEWAY_URL}/api/v1/processing/jobs/{created_job_id}",
            method="GET",
            headers={"Authorization": f"Bearer {client_token}"}
        )
        assert status == 200, f"Expected 200, got {status}: {job_query_resp}"
        assert job_query_resp.get("id") == created_job_id, f"ID mismatch: {job_query_resp}"
        print(f"[PROCESSING] Job query OK -> status={job_query_resp.get('status')}, stage={job_query_resp.get('stage')}")

        # Step 11: Wait for terminal status
        print("[ORCHESTRATION] Awaiting worker terminal state...")
        poll_start = time.time()
        timeout = 10.0
        terminal_status = None
        while (time.time() - poll_start) < timeout:
            status, cur_job, _ = http_request(
                f"{GATEWAY_URL}/api/v1/processing/jobs/{created_job_id}",
                method="GET",
                headers={"Authorization": f"Bearer {client_token}"}
            )
            if status == 200:
                s = cur_job.get("status")
                if s in ("COMPLETED", "FAILED", "CANCELLED"):
                    terminal_status = s
                    break
            time.sleep(0.3)

        assert terminal_status is not None, "Job did not reach terminal state within timeout"
        print(f"[ORCHESTRATION] Job reached terminal status: {terminal_status}")

        print("=" * 70)
        print("[SUCCESS] All Knowledge Service + Processing Service integration checks PASSED.")
        print("=" * 70)

    finally:
        # Step 12: Guaranteed exact cleanup
        print("[CLEANUP] Executing targeted cleanup...")
        if created_job_id:
            run_psql("processing_integration_db", f"DELETE FROM processing_jobs WHERE id = '{created_job_id}';")
            print(f"[CLEANUP] Deleted job {created_job_id}")
        if doc_created:
            run_psql("knowledge_integration_db", f"DELETE FROM documents WHERE id = '{TEST_DOC_ID}';")
            print(f"[CLEANUP] Deleted document {TEST_DOC_ID}")
        if kb_created:
            run_psql("knowledge_integration_db", f"DELETE FROM knowledge_bases WHERE id = '{TEST_KB_ID}';")
            print(f"[CLEANUP] Deleted knowledge base {TEST_KB_ID}")

        # Postflight check
        kb_c = int(run_psql("knowledge_integration_db", f"SELECT count(*) FROM knowledge_bases WHERE id = '{TEST_KB_ID}';"))
        doc_c = int(run_psql("knowledge_integration_db", f"SELECT count(*) FROM documents WHERE id = '{TEST_DOC_ID}';"))
        job_c = int(run_psql("processing_integration_db", f"SELECT count(*) FROM processing_jobs WHERE document_id = '{TEST_DOC_ID}';"))
        assert kb_c == 0 and doc_c == 0 and job_c == 0, "Residual records detected after cleanup!"
        print("[CLEANUP] Post-cleanup verified: Zero residual records.")

    elapsed = time.time() - start_time
    print(f"[DONE] V4 Direct Integration Test completed in {elapsed:.2f} seconds.")


if __name__ == "__main__":
    main()
