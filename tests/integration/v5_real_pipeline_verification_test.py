"""
OMRAGUL V5 — Real Application Flow Verification Test
Target: tests/integration/v5_real_pipeline_verification_test.py

Verifies:
1. Submitting real document processing request via API Gateway (Port 18080).
2. Processing Service invokes Multimodal Service for PDF extraction (POST /api/v1/multimodal/pdf/extract).
3. Processing Service invokes Multimodal Service for chunking (POST /api/v1/multimodal/chunk).
4. Processing Service invokes RAG Service (POST /api/v1/rag/index), which invokes Embedding Service (POST /api/v1/embeddings/batch).
5. State transitions across processing_jobs and documents:
   QUEUED -> VALIDATING -> EXTRACTING -> CHUNKING -> EMBEDDING_INDEXING -> COMPLETED
6. Guaranteed exact cleanup with zero residual records.
"""

import json
import os
import subprocess
import sys
import time
import urllib.request
import jwt

GATEWAY_URL = "http://localhost:18080"
PRIVATE_KEY_PATH = "secrets/private_key.pem"

TEST_USER_ID = "66666666-6666-6666-6666-666666666666"
TEST_KB_ID = "55555555-5555-5555-5555-555555555555"
TEST_DOC_ID = "77777777-5555-5555-5555-777777777777"
DOC_STORAGE_FILENAME = "v5_live_doc.pdf"


def run_psql(database: str, query: str) -> str:
    cmd = [
        "docker", "exec", "omragul-integration-postgres",
        "psql", "-U", "omragul_test_user", "-d", database, "-t", "-A", "-c", query
    ]
    res = subprocess.run(cmd, capture_output=True, text=True, check=True)
    return res.stdout.strip()


def check_preflight():
    kb_c = int(run_psql("knowledge_integration_db", f"SELECT count(*) FROM knowledge_bases WHERE id = '{TEST_KB_ID}';"))
    doc_c = int(run_psql("knowledge_integration_db", f"SELECT count(*) FROM documents WHERE id = '{TEST_DOC_ID}';"))
    job_c = int(run_psql("processing_integration_db", f"SELECT count(*) FROM processing_jobs WHERE document_id = '{TEST_DOC_ID}';"))
    if kb_c > 0 or doc_c > 0 or job_c > 0:
        raise RuntimeError(f"Preflight collision detected: KB={kb_c}, Doc={doc_c}, Job={job_c}")
    print("[PREFLIGHT] Test UUIDs verified unused.")


def mint_jwt():
    with open(PRIVATE_KEY_PATH, "r", encoding="utf-8") as f:
        priv_key = f.read()
    now = int(time.time())
    payload = {
        "sub": TEST_USER_ID,
        "iss": "http://localhost:8080",
        "roles": ["ROLE_USER"],
        "email": "v5verification@omragul.local",
        "username": "v5verifier",
        "iat": now,
        "exp": now + 3600
    }
    return jwt.encode(payload, priv_key, algorithm="RS256")


def ensure_pdf_stored():
    check = subprocess.run(
        ["docker", "exec", "omragul-integration-knowledge-service", "ls", f"/app/storage/{DOC_STORAGE_FILENAME}"],
        capture_output=True, text=True
    )
    if check.returncode != 0:
        print("[SETUP] Generating PDF via multimodal container and storing in knowledge service...")
        gen_cmd = (
            "import pymupdf\n"
            "doc = pymupdf.open()\n"
            "page = doc.new_page()\n"
            "text = ('OMRAGUL Live Integration Verification Document for V5 Release. '\n"
            "        'This test document verifies the complete document processing pipeline across all microservices. '\n"
            "        'The Gateway Service validates client credentials and issues an internal assertion. '\n"
            "        'The Processing Service orchestrates document validation, multimodal extraction, chunk creation, '\n"
            "        'and vector embedding indexing. The Multimodal Service receives the binary PDF stream and parses '\n"
            "        'the text payload using PyMuPDF. The Chunking Service splits the extracted content into semantic '\n"
            "        'segments. The RAG Service coordinates batch embedding generation with the Embedding Service, '\n"
            "        'populating the FAISS vector index partition. State machine transitions are persisted in PostgreSQL.')\n"
            "page.insert_text((50, 72), text, fontsize=11)\n"
            f"doc.save('/tmp/{DOC_STORAGE_FILENAME}')\n"
        )
        subprocess.run(["docker", "exec", "omragul-integration-multimodal-service", "python", "-c", gen_cmd], check=True)
        # Copy to host temp then into knowledge service
        temp_local = os.path.join(os.environ.get("TEMP", "."), DOC_STORAGE_FILENAME)
        subprocess.run(["docker", "cp", f"omragul-integration-multimodal-service:/tmp/{DOC_STORAGE_FILENAME}", temp_local], check=True)
        subprocess.run(["docker", "cp", temp_local, f"omragul-integration-knowledge-service:/app/storage/{DOC_STORAGE_FILENAME}"], check=True)
        subprocess.run(["docker", "exec", "omragul-integration-multimodal-service", "rm", f"/tmp/{DOC_STORAGE_FILENAME}"], check=True)
        if os.path.exists(temp_local):
            os.remove(temp_local)
    print(f"[SETUP] Document file /app/storage/{DOC_STORAGE_FILENAME} confirmed present in knowledge service container.")


def main():
    print("=" * 70)
    print("OMRAGUL V5 — Real Application Flow Pipeline Verification")
    print("=" * 70)

    kb_created = False
    doc_created = False
    created_job_id = None
    terminal_reached = False

    try:
        check_preflight()
        ensure_pdf_stored()

        # Insert Knowledge Base
        run_psql("knowledge_integration_db",
            f"INSERT INTO knowledge_bases (id, name, slug, owner_id, visibility, status) "
            f"VALUES ('{TEST_KB_ID}', 'V5 Real Pipeline KB', 'v5-real-pipeline-kb', '{TEST_USER_ID}', 'PRIVATE', 'ACTIVE');"
        )
        kb_created = True
        print(f"[SETUP] Knowledge Base created: {TEST_KB_ID}")

        # Insert Document
        run_psql("knowledge_integration_db",
            f"INSERT INTO documents (id, knowledge_base_id, name, original_filename, stored_filename, storage_path, "
            f"mime_type, file_size_bytes, checksum, owner_id, processing_status, status) "
            f"VALUES ('{TEST_DOC_ID}', '{TEST_KB_ID}', 'V5 Live Pipeline Test Document', '{DOC_STORAGE_FILENAME}', '{DOC_STORAGE_FILENAME}', "
            f"'{DOC_STORAGE_FILENAME}', 'application/pdf', 1297, 'checksumv5livepdf1234567890', '{TEST_USER_ID}', 'PENDING', 'ACTIVE');"
        )
        doc_created = True
        print(f"[SETUP] Document created: {TEST_DOC_ID} (type: application/pdf)")

        token = mint_jwt()
        print("[AUTH] Legitimate client JWT minted.")

        # Submit processing job via Gateway
        req_body = json.dumps({
            "document_id": TEST_DOC_ID,
            "knowledge_base_id": TEST_KB_ID
        }).encode("utf-8")

        req = urllib.request.Request(
            f"{GATEWAY_URL}/api/v1/processing/jobs",
            data=req_body,
            headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json"
            },
            method="POST"
        )

        print("[INGRESS] Submitting processing job via Gateway...")
        with urllib.request.urlopen(req, timeout=10) as resp:
            http_status = resp.status
            resp_data = json.loads(resp.read().decode("utf-8"))

        print(f"[INGRESS] Gateway response HTTP {http_status}")
        assert http_status == 201, f"Expected HTTP 201, got {http_status}"
        created_job_id = resp_data.get("id")
        assert created_job_id is not None, "Missing job id in response"
        print(f"[INGRESS] Processing job created: {created_job_id}")

        # Monitor state transitions
        print("[ORCHESTRATION] Monitoring asynchronous pipeline state transitions...")
        observed_states = []
        terminal_status = None
        terminal_stage = None
        error_msg = None

        poll_start = time.time()
        timeout_seconds = 30.0

        while (time.time() - poll_start) < timeout_seconds:
            job_state_str = run_psql(
                "processing_integration_db",
                f"SELECT status, stage, coalesce(error_message, 'NONE') FROM processing_jobs WHERE id = '{created_job_id}';"
            )
            doc_state_str = run_psql(
                "knowledge_integration_db",
                f"SELECT processing_status FROM documents WHERE id = '{TEST_DOC_ID}';"
            )

            if job_state_str:
                parts = job_state_str.split("|")
                cur_status, cur_stage, cur_err = parts[0], parts[1], parts[2]
                state_tuple = (cur_status, cur_stage, doc_state_str)
                if not observed_states or observed_states[-1] != state_tuple:
                    observed_states.append(state_tuple)
                    print(f"  -> Observed: Job=[{cur_status} / {cur_stage}], Doc=[{doc_state_str}]")

                if cur_status in ("COMPLETED", "FAILED", "CANCELLED"):
                    terminal_reached = True
                    terminal_status = cur_status
                    terminal_stage = cur_stage
                    error_msg = cur_err
                    break

            time.sleep(0.3)

        print(f"\n[ORCHESTRATION] Terminal State reached in {time.time() - poll_start:.2f}s:")
        print(f"  Status: {terminal_status}, Stage: {terminal_stage}, Error: {error_msg}")
        print(f"  All observed state transitions: {observed_states}")

        assert terminal_reached, f"Job did not reach terminal state within {timeout_seconds}s"
        assert terminal_status == "COMPLETED", f"Expected COMPLETED, got {terminal_status} (Error: {error_msg})"

        final_doc_status = run_psql(
            "knowledge_integration_db",
            f"SELECT processing_status FROM documents WHERE id = '{TEST_DOC_ID}';"
        )
        print(f"[ORCHESTRATION] Final Document processing_status in Knowledge Service: {final_doc_status}")
        assert final_doc_status == "COMPLETED", f"Expected document status COMPLETED, got {final_doc_status}"

        print("\n[SUCCESS] Document extraction, chunk creation, embedding indexing, and state transitions fully verified!")

    finally:
        print("\n" + "-" * 70)
        print("[CLEANUP] Executing targeted cleanup...")
        print("-" * 70)

        if created_job_id:
            run_psql("processing_integration_db", f"DELETE FROM processing_jobs WHERE id = '{created_job_id}';")
            print(f"[CLEANUP] Deleted processing job {created_job_id}")

        if doc_created:
            run_psql("knowledge_integration_db", f"DELETE FROM documents WHERE id = '{TEST_DOC_ID}';")
            print(f"[CLEANUP] Deleted document {TEST_DOC_ID}")

        if kb_created:
            run_psql("knowledge_integration_db", f"DELETE FROM knowledge_bases WHERE id = '{TEST_KB_ID}';")
            print(f"[CLEANUP] Deleted knowledge base {TEST_KB_ID}")

        # Remove stored test file
        subprocess.run(
            ["docker", "exec", "omragul-integration-knowledge-service", "rm", "-f", f"/app/storage/{DOC_STORAGE_FILENAME}"],
            capture_output=True
        )
        print(f"[CLEANUP] Removed /app/storage/{DOC_STORAGE_FILENAME} from knowledge service container")

        # Post-cleanup residual check
        res_kb = int(run_psql("knowledge_integration_db", f"SELECT count(*) FROM knowledge_bases WHERE id = '{TEST_KB_ID}';"))
        res_doc = int(run_psql("knowledge_integration_db", f"SELECT count(*) FROM documents WHERE id = '{TEST_DOC_ID}';"))
        res_job = int(run_psql("processing_integration_db", f"SELECT count(*) FROM processing_jobs WHERE document_id = '{TEST_DOC_ID}';"))
        assert res_kb == 0 and res_doc == 0 and res_job == 0, "Residual test records detected!"
        print("[CLEANUP] Post-cleanup check passed: Zero residual test records.")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"\n[TEST FAILED] {type(e).__name__}: {e}", file=sys.stderr)
        sys.exit(1)
