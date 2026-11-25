"""
Comprehensive security and authentication tests for Agent Service (Stage 3.7 Batch 2C-B):
1. Gateway Assertion Verification:
   - Valid assertion decodes claims and subject
   - Missing token raises ValueError
   - Missing server secret fails closed (HTTP 500 / MissingSecretException)
   - Invalid signature raises ValueError
   - Tampered payload raises ValueError
   - Malformed token format (no dot, multiple dots, empty) raises ValueError
   - Invalid signature format (non-hex, wrong length) raises ValueError
   - Invalid Base64URL encoding raises ValueError
   - Invalid JSON payload raises ValueError
   - Non-object payload raises ValueError
   - Wrong issuer raises ValueError
   - Missing subject claim raises ValueError
   - Invalid UUID subject raises ValueError
   - Missing request ID raises ValueError
   - Malformed/boolean/float timestamps raise ValueError
   - Future issued-at timestamp raises ValueError
   - Expired assertion beyond clock skew raises ValueError
   - Allowed clock skew boundary tests

2. User Identity Extraction & Anti-Spoofing:
   - Missing assertion rejected with HTTP 401
   - Internal secret alone rejected with HTTP 401 (no fallback for chat)
   - Internal secret + claimed X-User-Id rejected with HTTP 401
   - Raw X-User-Id alone rejected with HTTP 401
   - Anti-spoofing: X-User-Id mismatch rejected with HTTP 401
   - Anti-spoofing: matching X-User-Id accepted
   - Valid assertion without X-User-Id accepted (derives user_id from assertion sub)
   - Missing server secret fails closed with HTTP 500

3. Chat Route Protection & Pre-Execution Gate:
   - Health endpoint remains public (HTTP 200 without auth)
   - Unauthenticated request returns HTTP 401
   - Invalid assertion returns HTTP 401
   - Expired assertion returns HTTP 401
   - Tampered assertion returns HTTP 401
   - Internal secret only returns HTTP 401
   - Internal secret + spoofed X-User-Id returns HTTP 401
   - Mismatched raw X-User-Id returns HTTP 401
   - Missing server secret fails closed with HTTP 500
   - Empty query returns HTTP 400
   - Empty knowledge_base_id returns HTTP 400
   - Valid assertion succeeds (HTTP 200)

4. Protection of LLM & RAG Invocations:
   - Rejected requests NEVER invoke Ollama LLM (ainvoke)
   - Rejected requests NEVER invoke RAG search (search_rag)

5. Clean Header Propagation to Downstream RAG:
   - Original Gateway assertion forwarded unchanged
   - Verified User ID forwarded
   - Request ID forwarded
   - Caller service attribution set to 'agent-service'
   - Internal service secret included
   - Arbitrary client headers stripped and NOT forwarded
"""

import base64
import hashlib
import hmac
import json
import os
import sys
import time
import unittest
import uuid
from typing import Any, Dict, Optional
from unittest.mock import AsyncMock, MagicMock, patch

agent_service_path = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if agent_service_path not in sys.path:
    sys.path.insert(0, agent_service_path)

# Mock langchain_ollama if not installed
if "langchain_ollama" not in sys.modules:
    mock_llm_mod = MagicMock()
    sys.modules["langchain_ollama"] = mock_llm_mod

# Mock httpx if not installed
if "httpx" not in sys.modules:
    mock_httpx = MagicMock()
    mock_httpx.HTTPError = Exception
    mock_httpx.HTTPStatusError = Exception
    sys.modules["httpx"] = mock_httpx

from fastapi import HTTPException, Request
from fastapi.testclient import TestClient

import jwt
from cryptography.hazmat.primitives.asymmetric import rsa

from app.config.settings import settings
from app.main import app
from app.services.auth_service import (
    InMemoryGatewayAssertionReplayService,
    MissingSecretException,
    VerifiedIdentity,
    extract_and_verify_user_identity,
    set_cached_public_key,
    set_default_replay_service,
    verify_gateway_assertion,
)
from app.api import routes as api_routes


VALID_USER_UUID = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
OTHER_USER_UUID = "8ba91f24-1234-4562-b3fc-2c963f66afa7"
TEST_SECRET = "test-agent-internal-secret-67890"

TEST_PRIVATE_KEY = rsa.generate_private_key(
    public_exponent=65537,
    key_size=2048,
)
TEST_PUBLIC_KEY = TEST_PRIVATE_KEY.public_key()
ROGUE_PRIVATE_KEY = rsa.generate_private_key(
    public_exponent=65537,
    key_size=2048,
)


def create_test_gateway_assertion(
    user_id: str = VALID_USER_UUID,
    secret: str = TEST_SECRET,
    expired: bool = False,
    issuer: str = "gateway-service",
    aud: Optional[str] = "agent-service",
    rid: str = "req-agent-12345",
    jti: Optional[Any] = None,
    mth: Optional[Any] = "POST",
    pth: Optional[Any] = "/api/v1/agent/chat",
    iat: Optional[Any] = None,
    exp: Optional[Any] = None,
    roles: str = "ROLE_USER",
    private_key: Optional[rsa.RSAPrivateKey] = None,
) -> str:
    """Helper to create valid or test-conditioned RS256 Gateway Assertion tokens."""
    now = int(time.time())
    if iat is None:
        iat = now
    if exp is None:
        exp = (now - 100) if expired else (now + 60)

    payload_dict: Dict[str, Any] = {
        "sub": user_id,
        "roles": roles,
        "iss": issuer,
        "iat": iat,
        "exp": exp,
        "rid": rid
    }
    if aud != "OMIT":
        payload_dict["aud"] = aud
    if jti != "OMIT":
        payload_dict["jti"] = str(uuid.uuid4()) if jti is None else jti
    if mth != "OMIT":
        payload_dict["mth"] = mth
    if pth != "OMIT":
        payload_dict["pth"] = pth
    pk = private_key if private_key is not None else TEST_PRIVATE_KEY
    return jwt.encode(payload_dict, pk, algorithm="RS256", headers={"typ": "JWT"})


class TestAgentGatewayAssertionVerification(unittest.TestCase):
    """Direct unit tests for Gateway assertion token verification in Agent Service."""

    def setUp(self):
        os.environ["OMRAGUL_TEST_MODE"] = "true"
        self.secret = TEST_SECRET
        settings.INTERNAL_SERVICE_SECRET = self.secret
        set_cached_public_key(TEST_PUBLIC_KEY)
        set_default_replay_service(InMemoryGatewayAssertionReplayService())

    def test_valid_assertion_decodes_claims(self):
        token = create_test_gateway_assertion(user_id=VALID_USER_UUID, secret=self.secret)
        claims = verify_gateway_assertion(token)
        self.assertEqual(claims["sub"], VALID_USER_UUID)
        self.assertEqual(claims["iss"], "gateway-service")
        self.assertEqual(claims["rid"], "req-agent-12345")
        self.assertEqual(claims["roles"], "ROLE_USER")

    def test_missing_token_raises_value_error(self):
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion("")
        self.assertIn("Missing", str(ctx.exception))

    def test_legacy_hmac_rejected(self):
        token = "payloadb64.signaturehex1234567890abcdef1234567890abcdef1234567890abcdef12345678"
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token)
        self.assertIn("legacy hmac gateway assertions are rejected", str(ctx.exception).lower())

    def test_invalid_signature_raises_value_error(self):
        token = create_test_gateway_assertion(private_key=ROGUE_PRIVATE_KEY)
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token)
        self.assertIn("signature", str(ctx.exception).lower())

    def test_tampered_payload_raises_value_error(self):
        token = create_test_gateway_assertion(user_id=VALID_USER_UUID)
        parts = token.split(".")
        tampered_dict = {
            "sub": OTHER_USER_UUID,
            "roles": "ROLE_USER",
            "iss": "gateway-service",
            "aud": "agent-service",
            "jti": str(uuid.uuid4()),
            "mth": "POST",
            "pth": "/api/v1/agent/chat",
            "iat": int(time.time()),
            "exp": int(time.time()) + 60,
            "rid": "req-agent-12345"
        }
        tampered_b64 = base64.urlsafe_b64encode(json.dumps(tampered_dict).encode("utf-8")).decode("utf-8").rstrip("=")
        tampered_token = f"{parts[0]}.{tampered_b64}.{parts[2]}"
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(tampered_token)
        self.assertIn("signature", str(ctx.exception).lower())

    def test_malformed_token_format(self):
        with self.assertRaises(ValueError):
            verify_gateway_assertion("no-dot-token")
        with self.assertRaises(ValueError):
            verify_gateway_assertion("a.b.c.d")

    def test_invalid_signature_format(self):
        token = create_test_gateway_assertion()
        parts = token.split(".")
        with self.assertRaises(ValueError):
            verify_gateway_assertion(f"{parts[0]}.{parts[1]}.not-valid-base64-sig!!!")

    def test_invalid_base64_payload(self):
        token = create_test_gateway_assertion()
        parts = token.split(".")
        with self.assertRaises(ValueError):
            verify_gateway_assertion(f"{parts[0]}.!!!invalid-base64!!!.{parts[2]}")

    def test_invalid_json_payload(self):
        bad_json = base64.urlsafe_b64encode(b"not-json-content").decode("utf-8").rstrip("=")
        token = create_test_gateway_assertion()
        parts = token.split(".")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(f"{parts[0]}.{bad_json}.{parts[2]}")
        self.assertTrue("malformed" in str(ctx.exception).lower() or "json" in str(ctx.exception).lower())

    def test_non_object_json_payload(self):
        bad_payload = base64.urlsafe_b64encode(b'["list", "not", "object"]').decode("utf-8").rstrip("=")
        token = create_test_gateway_assertion()
        parts = token.split(".")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(f"{parts[0]}.{bad_payload}.{parts[2]}")
        self.assertIn("json object", str(ctx.exception).lower())

    def test_wrong_issuer(self):
        token = create_test_gateway_assertion(issuer="rogue-service")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token)
        self.assertIn("issuer", str(ctx.exception).lower())

    def test_missing_sub(self):
        token = create_test_gateway_assertion(user_id="")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token)
        self.assertIn("sub", str(ctx.exception).lower())

    def test_invalid_uuid_sub(self):
        token = create_test_gateway_assertion(user_id="not-a-valid-uuid")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token)
        self.assertIn("uuid", str(ctx.exception).lower())

    def test_missing_rid(self):
        token = create_test_gateway_assertion(rid="")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token)
        self.assertIn("rid", str(ctx.exception).lower())

    def test_boolean_and_non_int_timestamps(self):
        token = create_test_gateway_assertion(iat=True)
        with self.assertRaises(ValueError):
            verify_gateway_assertion(token)
        token2 = create_test_gateway_assertion(exp="1899999999")
        with self.assertRaises(ValueError):
            verify_gateway_assertion(token2)
        token3 = create_test_gateway_assertion(iat=12345.67)
        with self.assertRaises(ValueError):
            verify_gateway_assertion(token3)

    def test_future_iat_beyond_clock_skew(self):
        now = int(time.time())
        token = create_test_gateway_assertion(iat=now + 15)
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token)
        self.assertIn("future", str(ctx.exception).lower())

    def test_expired_assertion_beyond_clock_skew(self):
        now = int(time.time())
        token = create_test_gateway_assertion(exp=now - 15)
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token)
        self.assertIn("expired", str(ctx.exception).lower())

    def test_clock_skew_boundary(self):
        now = int(time.time())
        token_fut = create_test_gateway_assertion(iat=now + 8)
        claims_fut = verify_gateway_assertion(token_fut)
        self.assertEqual(claims_fut["sub"], VALID_USER_UUID)

        token_exp = create_test_gateway_assertion(exp=now - 8)
        claims_exp = verify_gateway_assertion(token_exp)
        self.assertEqual(claims_exp["sub"], VALID_USER_UUID)


class TestAgentUserIdentityAndAntiSpoofing(unittest.TestCase):
    """Direct unit tests for extract_and_verify_user_identity in Agent Service."""

    def setUp(self):
        os.environ["OMRAGUL_TEST_MODE"] = "true"
        self.secret = TEST_SECRET
        settings.INTERNAL_SERVICE_SECRET = self.secret
        set_cached_public_key(TEST_PUBLIC_KEY)
        set_default_replay_service(InMemoryGatewayAssertionReplayService())

    def _make_request(self, headers: Dict[str, str]) -> Request:
        scope = {
            "type": "http",
            "method": "POST",
            "path": "/api/v1/agent/chat",
            "headers": [(k.lower().encode("latin-1"), v.encode("latin-1")) for k, v in headers.items()],
        }
        return Request(scope)

    def test_missing_assertion_header_fails_401(self):
        req = self._make_request({})
        with self.assertRaises(HTTPException) as ctx:
            extract_and_verify_user_identity(req, self.secret)
        self.assertEqual(ctx.exception.status_code, 401)

    def test_internal_secret_only_fails_401(self):
        req = self._make_request({
            "X-Internal-Service-Secret": self.secret
        })
        with self.assertRaises(HTTPException) as ctx:
            extract_and_verify_user_identity(req, self.secret)
        self.assertEqual(ctx.exception.status_code, 401)

    def test_internal_secret_plus_claimed_user_fails_401(self):
        req = self._make_request({
            "X-Internal-Service-Secret": self.secret,
            "X-User-Id": VALID_USER_UUID
        })
        with self.assertRaises(HTTPException) as ctx:
            extract_and_verify_user_identity(req, self.secret)
        self.assertEqual(ctx.exception.status_code, 401)

    def test_raw_user_id_alone_fails_401(self):
        req = self._make_request({
            "X-User-Id": VALID_USER_UUID
        })
        with self.assertRaises(HTTPException) as ctx:
            extract_and_verify_user_identity(req, self.secret)
        self.assertEqual(ctx.exception.status_code, 401)

    def test_anti_spoofing_mismatch_fails_401(self):
        token = create_test_gateway_assertion(user_id=VALID_USER_UUID, secret=self.secret)
        req = self._make_request({
            "X-Gateway-Assertion": token,
            "X-User-Id": OTHER_USER_UUID
        })
        with self.assertRaises(HTTPException) as ctx:
            extract_and_verify_user_identity(req, self.secret)
        self.assertEqual(ctx.exception.status_code, 401)
        self.assertIn("spoofing", ctx.exception.detail.lower())

    def test_anti_spoofing_matching_user_id_succeeds(self):
        token = create_test_gateway_assertion(user_id=VALID_USER_UUID, secret=self.secret, rid="req-456")
        req = self._make_request({
            "X-Gateway-Assertion": token,
            "X-User-Id": VALID_USER_UUID
        })
        identity = extract_and_verify_user_identity(req, self.secret)
        self.assertEqual(identity.user_id, VALID_USER_UUID)
        self.assertEqual(identity.request_id, "req-456")
        self.assertEqual(identity.assertion_token, token)

    def test_valid_assertion_without_user_id_header_succeeds(self):
        token = create_test_gateway_assertion(user_id=VALID_USER_UUID, secret=self.secret, rid="req-789")
        req = self._make_request({
            "X-Gateway-Assertion": token
        })
        identity = extract_and_verify_user_identity(req, self.secret)
        self.assertEqual(identity.user_id, VALID_USER_UUID)
        self.assertEqual(identity.request_id, "req-789")
        self.assertEqual(identity.assertion_token, token)

    def test_missing_server_secret_fails_closed_500(self):
        token = create_test_gateway_assertion(user_id=VALID_USER_UUID, secret=self.secret)
        req = self._make_request({
            "X-Gateway-Assertion": token
        })
        set_cached_public_key(None)
        try:
            with self.assertRaises(HTTPException) as ctx:
                extract_and_verify_user_identity(req, public_key=None)
            self.assertEqual(ctx.exception.status_code, 500)
        finally:
            set_cached_public_key(TEST_PUBLIC_KEY)


class TestAgentChatRouteAndPropagation(unittest.IsolatedAsyncioTestCase):
    """Integration and security tests for Agent chat route, execution gate, and header propagation."""

    def setUp(self):
        os.environ["OMRAGUL_TEST_MODE"] = "true"
        self.secret = TEST_SECRET
        settings.INTERNAL_SERVICE_SECRET = self.secret
        set_cached_public_key(TEST_PUBLIC_KEY)
        set_default_replay_service(InMemoryGatewayAssertionReplayService())
        settings.RAG_SERVICE_URL = "http://localhost:8083"
        self.client = TestClient(app)
        self.client.__enter__()

    def tearDown(self):
        self.client.__exit__(None, None, None)

    def test_health_endpoint_public(self):
        resp = self.client.get("/health")
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json()["status"], "UP")

    def test_chat_unauthenticated_returns_401(self):
        resp = self.client.post(
            "/api/v1/agent/chat",
            json={"query": "Test query", "knowledge_base_id": "kb-1"}
        )
        self.assertEqual(resp.status_code, 401)

    def test_chat_invalid_assertion_returns_401(self):
        resp = self.client.post(
            "/api/v1/agent/chat",
            headers={"X-Gateway-Assertion": "bad.token"},
            json={"query": "Test query", "knowledge_base_id": "kb-1"}
        )
        self.assertEqual(resp.status_code, 401)

    def test_chat_expired_assertion_returns_401(self):
        token = create_test_gateway_assertion(expired=True, secret=self.secret)
        resp = self.client.post(
            "/api/v1/agent/chat",
            headers={"X-Gateway-Assertion": token},
            json={"query": "Test query", "knowledge_base_id": "kb-1"}
        )
        self.assertEqual(resp.status_code, 401)

    def test_chat_tampered_assertion_returns_401(self):
        token = create_test_gateway_assertion(user_id=VALID_USER_UUID, secret=self.secret)
        parts = token.split(".")
        tampered_token = f"{parts[0]}.dGFtcGVyZWQ.{parts[2]}"
        resp = self.client.post(
            "/api/v1/agent/chat",
            headers={"X-Gateway-Assertion": tampered_token},
            json={"query": "Test query", "knowledge_base_id": "kb-1"}
        )
        self.assertEqual(resp.status_code, 401)

    def test_chat_legacy_hmac_returns_401(self):
        resp = self.client.post(
            "/api/v1/agent/chat",
            headers={"X-Gateway-Assertion": "payloadb64.fakehmacsignature1234567890abcdef1234567890abcdef1234567890abcdef12"},
            json={"query": "Test query", "knowledge_base_id": "kb-1"}
        )
        self.assertEqual(resp.status_code, 401)

    def test_chat_spoofed_user_id_returns_401(self):
        token = create_test_gateway_assertion(user_id=VALID_USER_UUID, secret=self.secret)
        resp = self.client.post(
            "/api/v1/agent/chat",
            headers={
                "X-Gateway-Assertion": token,
                "X-User-Id": OTHER_USER_UUID
            },
            json={"query": "Test query", "knowledge_base_id": "kb-1"}
        )
        self.assertEqual(resp.status_code, 401)

    def test_chat_internal_secret_only_returns_401(self):
        resp = self.client.post(
            "/api/v1/agent/chat",
            headers={"X-Internal-Service-Secret": self.secret},
            json={"query": "Test query", "knowledge_base_id": "kb-1"}
        )
        self.assertEqual(resp.status_code, 401)

    def test_chat_missing_server_secret_fails_closed_500(self):
        set_cached_public_key(None)
        try:
            token = create_test_gateway_assertion(user_id=VALID_USER_UUID, secret=self.secret)
            resp = self.client.post(
                "/api/v1/agent/chat",
                headers={"X-Gateway-Assertion": token},
                json={"query": "Test query", "knowledge_base_id": "kb-1"}
            )
            self.assertEqual(resp.status_code, 500)
        finally:
            set_cached_public_key(TEST_PUBLIC_KEY)
        # Restore secret
        settings.INTERNAL_SERVICE_SECRET = self.secret

    def test_chat_empty_query_returns_400(self):
        token = create_test_gateway_assertion(user_id=VALID_USER_UUID, secret=self.secret)
        resp = self.client.post(
            "/api/v1/agent/chat",
            headers={"X-Gateway-Assertion": token},
            json={"query": "   ", "knowledge_base_id": "kb-1"}
        )
        self.assertEqual(resp.status_code, 400)
        self.assertIn("query cannot be empty", resp.json()["detail"].lower())

    def test_chat_empty_knowledge_base_id_returns_400(self):
        token = create_test_gateway_assertion(user_id=VALID_USER_UUID, secret=self.secret)
        resp = self.client.post(
            "/api/v1/agent/chat",
            headers={"X-Gateway-Assertion": token},
            json={"query": "Valid query", "knowledge_base_id": "   "}
        )
        self.assertEqual(resp.status_code, 400)
        self.assertIn("knowledge_base_id is required", resp.json()["detail"].lower())

    async def test_unauthenticated_request_never_invokes_llm_or_rag(self):
        """Verify that an unauthenticated request is blocked BEFORE any LLM or RAG invocation."""
        with patch.object(api_routes.agent_service, "chat", new_callable=AsyncMock) as mock_chat:
            resp = self.client.post(
                "/api/v1/agent/chat",
                json={"query": "Tell me secrets", "knowledge_base_id": "kb-secret"}
            )
            self.assertEqual(resp.status_code, 401)
            mock_chat.assert_not_called()

    async def test_spoofed_request_never_invokes_llm_or_rag(self):
        """Verify that a spoofed request is blocked BEFORE any LLM or RAG invocation."""
        token = create_test_gateway_assertion(user_id=VALID_USER_UUID, secret=self.secret)
        with patch.object(api_routes.agent_service, "chat", new_callable=AsyncMock) as mock_chat:
            resp = self.client.post(
                "/api/v1/agent/chat",
                headers={
                    "X-Gateway-Assertion": token,
                    "X-User-Id": OTHER_USER_UUID
                },
                json={"query": "Tell me secrets", "knowledge_base_id": "kb-secret"}
            )
            self.assertEqual(resp.status_code, 401)
            mock_chat.assert_not_called()

    async def test_chat_success_and_clean_header_propagation(self):
        """
        Verify:
        1. Valid assertion succeeds with HTTP 200.
        2. Verified context propagates as structured, clean headers.
        3. Arbitrary client headers are NOT forwarded.
        """
        token = create_test_gateway_assertion(
            user_id=VALID_USER_UUID,
            secret=self.secret,
            rid="req-agent-test-999"
        )

        mock_sources = [{"document_id": "doc-1", "text": "Relevant context"}]
        mock_response_text = "Generated answer from LLM"

        with patch.object(
            api_routes.agent_service,
            "chat",
            new_callable=AsyncMock,
            return_value=(mock_response_text, mock_sources)
        ) as mock_chat:
            resp = self.client.post(
                "/api/v1/agent/chat",
                headers={
                    "X-Gateway-Assertion": token,
                    "X-User-Id": VALID_USER_UUID,
                    "X-Custom-Client-Header": "arbitrary-injection-attempt",
                    "Authorization": "Bearer rogue-token",
                    "Cookie": "session=malicious"
                },
                json={"query": "What is the policy?", "knowledge_base_id": "kb-policy"}
            )

            self.assertEqual(resp.status_code, 200)
            data = resp.json()
            self.assertEqual(data["response"], mock_response_text)
            self.assertEqual(len(data["sources"]), 1)
            self.assertEqual(data["sources"][0]["document_id"], "doc-1")

            mock_chat.assert_called_once()
            call_kwargs = mock_chat.call_args[1]
            self.assertEqual(call_kwargs["query"], "What is the policy?")
            self.assertEqual(call_kwargs["knowledge_base_id"], "kb-policy")

            forwarded_headers = call_kwargs["headers"]
            # 1. Assert trusted headers are present and exact
            self.assertEqual(forwarded_headers["X-Gateway-Assertion"], token)
            self.assertEqual(forwarded_headers["X-User-Id"], VALID_USER_UUID)
            self.assertEqual(forwarded_headers["X-Request-Id"], "req-agent-test-999")
            self.assertEqual(forwarded_headers["X-Internal-Caller-Service"], "agent-service")
            # Finding 2C.1-01: X-Internal-Service-Secret must NOT be present in user-scoped search headers
            self.assertNotIn("X-Internal-Service-Secret", forwarded_headers)

            # 2. Assert arbitrary inbound headers are NOT forwarded
            self.assertNotIn("X-Custom-Client-Header", forwarded_headers)
            self.assertNotIn("Authorization", forwarded_headers)
            self.assertNotIn("Cookie", forwarded_headers)


if __name__ == "__main__":
    unittest.main()
