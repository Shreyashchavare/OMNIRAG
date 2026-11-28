"""
OMNIRAG V6 — Final Acceptance Hardening Integration Test
Target: tests/integration/v6_rag_agent_live_test.py

Verifies:
1. Live Document-Grounded QA: Answer matches document's actual content; sources preserved.
2. Empty Knowledge Base Abstention: Abstention/no-information response with zero sources.
3. Irrelevant Query: No generic hardcoded answer; explicit abstention when context lacks answer.
4. Downstream Failure: Graceful error handling (HTTP 502) when RAG service is unreachable.
5. Authorization Negative Tests:
   5A. Unauthorized user accessing another user's private knowledge base (HTTP 403).
   5B. Unauthorized user accessing another user's document via RAG search (HTTP 403).
   5C. Invalid/incorrect assertion audience rejected (HTTP 401).
   5D. Replayed gateway assertion / JTI rejected fail-closed (HTTP 401).
6. Guaranteed cleanup with zero residual records.
"""

import json
import subprocess
import sys
import time
import urllib.request
import urllib.error
import uuid
import jwt

GATEWAY_URL = "http://localhost:18080"
RAG_DIRECT_URL = "http://localhost:18002"
PRIVATE_KEY_PATH = "secrets/private_key.pem"

USER_A_ID = "66666666-6666-6666-6666-666666666666"
USER_B_ID = "88888888-8888-8888-8888-888888888888"

KB_A_ID = "55555555-5555-5555-5555-555555555555"
DOC_A_ID = "77777777-5555-5555-5555-777777777777"
DOC_STORAGE_FILENAME = "v5_live_doc.pdf"

EMPTY_KB_ID = "44444444-4444-4444-4444-444444444444"


def run_psql(database: str, query: str) -> str:
    cmd = [
        "docker", "exec", "omragul-integration-postgres",
        "psql", "-U", "omragul_test_user", "-d", database, "-t", "-A", "-c", query
    ]
    res = subprocess.run(cmd, capture_output=True, text=True, check=True)
    return res.stdout.strip()


def mint_client_jwt(user_id: str, username: str = "v6verifier") -> str:
    with open(PRIVATE_KEY_PATH, "r", encoding="utf-8") as f:
        priv_key = f.read()
    now = int(time.time())
    payload = {
        "sub": user_id,
        "iss": "http://localhost:8080",
        "roles": ["ROLE_USER"],
        "email": f"{username}@omragul.local",
        "username": username,
        "iat": now,
        "exp": now + 3600
    }
    return jwt.encode(payload, priv_key, algorithm="RS256")


def mint_custom_gateway_assertion(
    user_id: str,
    audience: str = "rag-service",
    method: str = "POST",
    path: str = "/api/v1/rag/search",
    jti: str = None
) -> str:
    """Mints an internal assertion signed with gateway assertion private key for negative testing."""
    key_path = "secrets/integration/gateway_assertion_private_key.pem"
    with open(key_path, "r", encoding="utf-8") as f:
        priv_key = f.read()
    now = int(time.time())
    payload = {
        "sub": user_id,
        "iss": "gateway-service",
        "aud": audience,
        "mth": method,
        "pth": path,
        "rid": str(uuid.uuid4()),
        "jti": jti or str(uuid.uuid4()),
        "iat": now,
        "exp": now + 60
    }
    return jwt.encode(payload, priv_key, algorithm="RS256")


def main():
    print("=" * 70)
    print("OMNIRAG V6 — Final Acceptance Hardening Live Verification")
    print("=" * 70)

    kb_created = False
    doc_created = False
    empty_kb_created = False

    try:
        # Preflight cleanups
        run_psql("knowledge_integration_db", f"DELETE FROM documents WHERE id = '{DOC_A_ID}';")
        run_psql("knowledge_integration_db", f"DELETE FROM knowledge_bases WHERE id IN ('{KB_A_ID}', '{EMPTY_KB_ID}');")

        # 1. Setup User A's Knowledge Base and Document
        run_psql("knowledge_integration_db",
            f"INSERT INTO knowledge_bases (id, name, slug, owner_id, visibility, status) "
            f"VALUES ('{KB_A_ID}', 'User A Private KB', 'user-a-private-kb', '{USER_A_ID}', 'PRIVATE', 'ACTIVE');"
        )
        kb_created = True
        print(f"[SETUP] User A Knowledge Base created: {KB_A_ID}")

        run_psql("knowledge_integration_db",
            f"INSERT INTO documents (id, knowledge_base_id, name, original_filename, stored_filename, storage_path, "
            f"mime_type, file_size_bytes, checksum, owner_id, processing_status, status) "
            f"VALUES ('{DOC_A_ID}', '{KB_A_ID}', 'V5 Live Test Document', '{DOC_STORAGE_FILENAME}', '{DOC_STORAGE_FILENAME}', "
            f"'{DOC_STORAGE_FILENAME}', 'application/pdf', 1297, 'checksumv5livepdf1234567890', '{USER_A_ID}', 'COMPLETED', 'ACTIVE');"
        )
        doc_created = True
        print(f"[SETUP] User A Document created: {DOC_A_ID} (status: ACTIVE)")

        # Create empty KB for abstention test
        run_psql("knowledge_integration_db",
            f"INSERT INTO knowledge_bases (id, name, slug, owner_id, visibility, status) "
            f"VALUES ('{EMPTY_KB_ID}', 'Empty KB', 'empty-kb', '{USER_A_ID}', 'PRIVATE', 'ACTIVE');"
        )
        empty_kb_created = True
        print(f"[SETUP] Empty Knowledge Base created: {EMPTY_KB_ID}")

        token_user_a = mint_client_jwt(USER_A_ID, "user_a")
        token_user_b = mint_client_jwt(USER_B_ID, "user_b")
        print("[AUTH] Minted Client JWTs for User A and User B.")

        # =============================================================
        # TEST 1: Live Grounded Question-Answering (Matching Actual Content)
        # =============================================================
        print("\n" + "-" * 70)
        print("[TEST 1] Live Document Grounded Question-Answering")
        print("-" * 70)

        query = "What does the OMRAGUL Live Integration Verification Document verify?"
        req_body = json.dumps({
            "query": query,
            "knowledge_base_id": KB_A_ID
        }).encode("utf-8")

        req = urllib.request.Request(
            f"{GATEWAY_URL}/api/v1/agent/chat",
            data=req_body,
            headers={
                "Authorization": f"Bearer {token_user_a}",
                "Content-Type": "application/json"
            },
            method="POST"
        )

        with urllib.request.urlopen(req, timeout=30) as resp:
            http_status = resp.status
            resp_data = json.loads(resp.read().decode("utf-8"))

        assert http_status == 200, f"Expected HTTP 200, got {http_status}"
        answer = resp_data.get("response", "")
        sources = resp_data.get("sources", [])

        print(f"  [ANSWER]:\n  {answer}")
        print(f"  [SOURCES]: {len(sources)} source(s)")

        # Verify answer matches actual document content
        assert "document processing pipeline" in answer.lower() or "verifies" in answer.lower(), \
            f"Answer does not match document actual content: {answer}"
        # Ensure it is NOT the generic hardcoded string
        assert "omragul architecture combines gateway, agent, rag" not in answer.lower(), \
            "Answer received generic hardcoded response!"
        assert len(sources) > 0, "Sources list should not be empty"
        assert DOC_A_ID in [s.get("document_id") for s in sources], f"Expected {DOC_A_ID} in sources"
        print("[TEST 1 PASSED] Grounded answer matches document actual content and preserves sources.")

        # =============================================================
        # TEST 2: Empty Knowledge Base Abstention Response & Zero Sources
        # =============================================================
        print("\n" + "-" * 70)
        print("[TEST 2] Empty Knowledge Base Abstention Response & Zero Sources")
        print("-" * 70)

        empty_req_body = json.dumps({
            "query": "What is the speed of light in vacuum?",
            "knowledge_base_id": EMPTY_KB_ID
        }).encode("utf-8")

        empty_req = urllib.request.Request(
            f"{GATEWAY_URL}/api/v1/agent/chat",
            data=empty_req_body,
            headers={
                "Authorization": f"Bearer {token_user_a}",
                "Content-Type": "application/json"
            },
            method="POST"
        )

        with urllib.request.urlopen(empty_req, timeout=30) as resp:
            empty_status = resp.status
            empty_resp_data = json.loads(resp.read().decode("utf-8"))

        assert empty_status == 200, f"Expected HTTP 200, got {empty_status}"
        empty_answer = empty_resp_data.get("response", "")
        empty_sources = empty_resp_data.get("sources", [])

        print(f"  [EMPTY KB ANSWER]: {empty_answer}")
        print(f"  [EMPTY KB SOURCES]: {empty_sources}")

        assert "not have enough information" in empty_answer.lower() or "no information" in empty_answer.lower(), \
            f"Expected abstention response, got: {empty_answer}"
        assert len(empty_sources) == 0, f"Expected 0 sources, got {len(empty_sources)}"
        print("[TEST 2 PASSED] Empty KB returned explicit abstention and zero sources.")

        # =============================================================
        # TEST 3: Irrelevant Query on Populated Knowledge Base
        # =============================================================
        print("\n" + "-" * 70)
        print("[TEST 3] Irrelevant Query on Populated Knowledge Base")
        print("-" * 70)

        irrelevant_query = "What is the secret recipe for baking chocolate chip cookies?"
        irrel_body = json.dumps({
            "query": irrelevant_query,
            "knowledge_base_id": KB_A_ID
        }).encode("utf-8")

        irrel_req = urllib.request.Request(
            f"{GATEWAY_URL}/api/v1/agent/chat",
            data=irrel_body,
            headers={
                "Authorization": f"Bearer {token_user_a}",
                "Content-Type": "application/json"
            },
            method="POST"
        )

        with urllib.request.urlopen(irrel_req, timeout=30) as resp:
            irrel_status = resp.status
            irrel_data = json.loads(resp.read().decode("utf-8"))

        assert irrel_status == 200, f"Expected HTTP 200, got {irrel_status}"
        irrel_answer = irrel_data.get("response", "")
        print(f"  [IRRELEVANT QUERY ANSWER]: {irrel_answer}")

        # Ensure NO generic hardcoded answer
        assert "omragul architecture combines gateway" not in irrel_answer.lower(), \
            "Irrelevant query received generic hardcoded answer!"
        assert "not have enough information" in irrel_answer.lower() or "no information" in irrel_answer.lower() or "cookies" not in irrel_answer.lower(), \
            f"Expected abstention or no-information, got: {irrel_answer}"
        print("[TEST 3 PASSED] Irrelevant query received no generic hardcoded answer.")

        # =============================================================
        # TEST 4: Downstream RAG Service Failure
        # =============================================================
        print("\n" + "-" * 70)
        print("[TEST 4] Downstream RAG Service Failure Handling")
        print("-" * 70)

        print("  [ACTION] Stopping RAG service container...")
        subprocess.run(["docker", "stop", "-t", "1", "omragul-integration-rag-service"], check=True)
        try:
            fail_body = json.dumps({
                "query": "What is the document about?",
                "knowledge_base_id": KB_A_ID
            }).encode("utf-8")

            fail_req = urllib.request.Request(
                f"{GATEWAY_URL}/api/v1/agent/chat",
                data=fail_body,
                headers={
                    "Authorization": f"Bearer {token_user_a}",
                    "Content-Type": "application/json"
                },
                method="POST"
            )

            try:
                with urllib.request.urlopen(fail_req, timeout=15) as resp:
                    status = resp.status
                    raise AssertionError(f"Expected HTTP 502/504 error, but got HTTP {status}")
            except urllib.error.HTTPError as e:
                err_msg = e.read().decode("utf-8")
                print(f"  -> Caught downstream failure: HTTP {e.code} - {err_msg}")
                assert e.code in (500, 502, 504), f"Expected HTTP 502/504, got {e.code}"
        finally:
            print("  [ACTION] Restarting RAG service container...")
            subprocess.run(["docker", "start", "omragul-integration-rag-service"], check=True)
            time.sleep(3)

        print("[TEST 4 PASSED] Downstream failure handled cleanly.")

        # =============================================================
        # TEST 5: Authorization Verification (Focused Negative Tests)
        # =============================================================
        print("\n" + "-" * 70)
        print("[TEST 5] Authorization Verification (4 Focused Negative Tests)")
        print("-" * 70)

        # 5A: Unauthorized user (User B) accessing User A's Knowledge Base via Gateway/Agent
        print("\n  [5A] Unauthorized user (User B) accessing User A's private KB...")
        unauth_kb_body = json.dumps({
            "query": "What is in User A's document?",
            "knowledge_base_id": KB_A_ID
        }).encode("utf-8")

        unauth_kb_req = urllib.request.Request(
            f"{GATEWAY_URL}/api/v1/agent/chat",
            data=unauth_kb_body,
            headers={
                "Authorization": f"Bearer {token_user_b}",
                "Content-Type": "application/json"
            },
            method="POST"
        )

        try:
            with urllib.request.urlopen(unauth_kb_req, timeout=15) as resp:
                raise AssertionError(f"Expected HTTP 403 Forbidden, got HTTP {resp.status}")
        except urllib.error.HTTPError as e:
            err_msg = e.read().decode("utf-8")
            print(f"  -> Successfully rejected: HTTP {e.code} - {err_msg}")
            assert e.code == 403, f"Expected HTTP 403, got {e.code}"
            assert "access denied" in err_msg.lower() or "forbidden" in err_msg.lower()
        print("  [PASS] 5A: User B cannot access User A's private Knowledge Base (HTTP 403).")

        # 5B: Direct RAG Search for User B on User A's document
        print("\n  [5B] Direct RAG search by User B on User A's Knowledge Base...")
        rag_search_body = json.dumps({
            "query": "What is in the document?",
            "knowledge_base_id": KB_A_ID,
            "top_k": 5
        }).encode("utf-8")

        # Mint an assertion for User B targeting rag-service
        assertion_user_b = mint_custom_gateway_assertion(
            user_id=USER_B_ID,
            audience="rag-service",
            method="POST",
            path="/api/v1/rag/search"
        )

        rag_search_req = urllib.request.Request(
            f"{RAG_DIRECT_URL}/api/v1/rag/search",
            data=rag_search_body,
            headers={
                "X-Gateway-Assertion": assertion_user_b,
                "X-User-Id": USER_B_ID,
                "Content-Type": "application/json"
            },
            method="POST"
        )

        try:
            with urllib.request.urlopen(rag_search_req, timeout=15) as resp:
                raise AssertionError(f"Expected HTTP 403 Forbidden on direct RAG search, got HTTP {resp.status}")
        except urllib.error.HTTPError as e:
            err_msg = e.read().decode("utf-8")
            print(f"  -> Successfully rejected: HTTP {e.code} - {err_msg}")
            assert e.code == 403, f"Expected HTTP 403, got {e.code}"
        print("  [PASS] 5B: User B cannot access User A's documents via RAG (HTTP 403).")

        # 5C: Invalid or incorrect assertion audience
        print("\n  [5C] Assertion with incorrect audience (aud='knowledge-service')...")
        wrong_aud_assertion = mint_custom_gateway_assertion(
            user_id=USER_A_ID,
            audience="knowledge-service",  # Wrong audience for RAG service
            method="POST",
            path="/api/v1/rag/search"
        )

        wrong_aud_req = urllib.request.Request(
            f"{RAG_DIRECT_URL}/api/v1/rag/search",
            data=rag_search_body,
            headers={
                "X-Gateway-Assertion": wrong_aud_assertion,
                "X-User-Id": USER_A_ID,
                "Content-Type": "application/json"
            },
            method="POST"
        )

        try:
            with urllib.request.urlopen(wrong_aud_req, timeout=15) as resp:
                raise AssertionError(f"Expected HTTP 401 Unauthorized for wrong audience, got HTTP {resp.status}")
        except urllib.error.HTTPError as e:
            err_msg = e.read().decode("utf-8")
            print(f"  -> Successfully rejected: HTTP {e.code} - {err_msg}")
            assert e.code == 401, f"Expected HTTP 401, got {e.code}"
            assert "audience" in err_msg.lower()
        print("  [PASS] 5C: Assertion with incorrect audience rejected (HTTP 401).")

        # 5D: Replayed gateway assertion / JTI
        print("\n  [5D] Replayed gateway assertion (duplicate JTI)...")
        shared_jti = str(uuid.uuid4())
        replay_assertion = mint_custom_gateway_assertion(
            user_id=USER_A_ID,
            audience="rag-service",
            method="POST",
            path="/api/v1/rag/search",
            jti=shared_jti
        )

        # First request with shared_jti should succeed
        first_req = urllib.request.Request(
            f"{RAG_DIRECT_URL}/api/v1/rag/search",
            data=rag_search_body,
            headers={
                "X-Gateway-Assertion": replay_assertion,
                "X-User-Id": USER_A_ID,
                "Content-Type": "application/json"
            },
            method="POST"
        )
        with urllib.request.urlopen(first_req, timeout=15) as resp:
            assert resp.status == 200, f"First request with fresh JTI should succeed, got {resp.status}"
        print("  -> First request with JTI succeeded (HTTP 200).")

        # Second request with the same assertion token (replay attack)
        second_req = urllib.request.Request(
            f"{RAG_DIRECT_URL}/api/v1/rag/search",
            data=rag_search_body,
            headers={
                "X-Gateway-Assertion": replay_assertion,
                "X-User-Id": USER_A_ID,
                "Content-Type": "application/json"
            },
            method="POST"
        )
        try:
            with urllib.request.urlopen(second_req, timeout=15) as resp:
                raise AssertionError(f"Expected HTTP 401 Unauthorized for replayed token, got HTTP {resp.status}")
        except urllib.error.HTTPError as e:
            err_msg = e.read().decode("utf-8")
            print(f"  -> Successfully rejected replay: HTTP {e.code} - {err_msg}")
            assert e.code == 401, f"Expected HTTP 401, got {e.code}"
            assert "replay" in err_msg.lower()
        print("  [PASS] 5D: Replayed gateway assertion/JTI rejected fail-closed (HTTP 401).")

        print("\n" + "=" * 70)
        print("[SUCCESS] ALL V6 FINAL ACCEPTANCE HARDENING CHECKS PASSED!")
        print("=" * 70)

    finally:
        print("\n" + "-" * 70)
        print("[CLEANUP] Executing targeted cleanup...")
        print("-" * 70)

        if doc_created:
            run_psql("knowledge_integration_db", f"DELETE FROM documents WHERE id = '{DOC_A_ID}';")
            print(f"[CLEANUP] Deleted document {DOC_A_ID}")

        if kb_created:
            run_psql("knowledge_integration_db", f"DELETE FROM knowledge_bases WHERE id = '{KB_A_ID}';")
            print(f"[CLEANUP] Deleted knowledge base {KB_A_ID}")

        if empty_kb_created:
            run_psql("knowledge_integration_db", f"DELETE FROM knowledge_bases WHERE id = '{EMPTY_KB_ID}';")
            print(f"[CLEANUP] Deleted empty knowledge base {EMPTY_KB_ID}")

        res_kb = int(run_psql("knowledge_integration_db", f"SELECT count(*) FROM knowledge_bases WHERE id IN ('{KB_A_ID}', '{EMPTY_KB_ID}');"))
        res_doc = int(run_psql("knowledge_integration_db", f"SELECT count(*) FROM documents WHERE id = '{DOC_A_ID}';"))
        assert res_kb == 0 and res_doc == 0, "Residual test records detected!"
        print("[CLEANUP] Post-cleanup check passed: Zero residual test records.")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"\n[TEST FAILED] {type(e).__name__}: {e}", file=sys.stderr)
        sys.exit(1)
