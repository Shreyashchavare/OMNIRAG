"""
Comprehensive security and route tests for RAG service (Stage 3.7 Batch 2C-A):
1. Gateway Assertion Verification:
   - Valid assertion decodes claims and subject
   - Missing token raises ValueError
   - Missing server secret fails closed (HTTP 500 / MissingSecretException)
   - Invalid signature raises ValueError
   - Tampered payload raises ValueError
   - Malformed token format (no dot, multiple dots) raises ValueError
   - Invalid signature format (non-hex, wrong length) raises ValueError
   - Invalid Base64URL encoding raises ValueError
   - Invalid JSON payload raises ValueError
   - Non-object payload raises ValueError
   - Wrong issuer raises ValueError
   - Missing subject claim raises ValueError
   - Invalid UUID subject raises ValueError
   - Missing request ID raises ValueError
   - Malformed/boolean timestamps raise ValueError
   - Future issued-at timestamp raises ValueError
   - Expired assertion beyond clock skew raises ValueError

2. User Identity Extraction & Anti-Spoofing:
   - Missing assertion rejected with HTTP 401
   - Internal secret alone rejected with HTTP 401 (no fallback for search)
   - Internal secret + claimed X-User-Id rejected with HTTP 401
   - Anti-spoofing: X-User-Id mismatch rejected with HTTP 401
   - Anti-spoofing: matching X-User-Id accepted
   - Missing server secret fails closed with HTTP 500

3. Query Embedding Protection:
   - No credentials rejected with HTTP 401
   - Invalid credentials rejected with HTTP 401
   - Raw X-User-Id alone rejected with HTTP 401
   - Valid Gateway Assertion accepted
   - Valid Internal Service Secret accepted
   - Missing server secret fails closed with HTTP 500
   - Empty query rejected with HTTP 400

4. Knowledge Base Access Verification & Propagation:
   - Forwards exact Gateway Assertion, User-Id, Request-Id, Internal Caller attribution
   - Missing Gateway Assertion fails closed without making HTTP call
   - HTTP 200 returns True
   - HTTP 401/403 returns False (access denied)
   - HTTP 404 returns False (not found)
   - HTTP 500 / network error fails closed with HTTP 502

5. Search Route Tests:
   - Valid assertion succeeds and returns SearchResponse
   - Missing assertion returns HTTP 401
   - Expired assertion returns HTTP 401
   - Tampered assertion returns HTTP 401
   - Internal secret only returns HTTP 401
   - Internal secret + spoofed X-User-Id returns HTTP 401
   - Mismatched raw X-User-Id returns HTTP 401
   - Unauthorized KB returns HTTP 403

6. Service-to-Service Internal Authentication & Isolation:
   - Internal service call accepts valid secret
   - Internal service call rejects missing/invalid secret
   - Gateway Assertion cannot access internal service endpoints
   - Scoped retrieval and document deletion
   - Authoritative document status & anti-resurrection
"""

import base64
import hashlib
import hmac
import json
import os
import shutil
import sys
import tempfile
import time
import unittest
import uuid
from typing import Any, Dict, Optional
from unittest.mock import ANY, AsyncMock, MagicMock, patch

from fastapi import HTTPException
from fastapi.testclient import TestClient

# Ensure test paths are in sys.path
rag_service_path = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if rag_service_path not in sys.path:
    sys.path.insert(0, rag_service_path)

import jwt
from cryptography.hazmat.primitives.asymmetric import rsa

from app.config.settings import settings
from app.main import app
from app.services.auth_service import (
    InMemoryGatewayAssertionReplayService,
    MissingSecretException,
    MissingPublicKeyException,
    VerifiedIdentity,
    extract_and_verify_user_identity,
    filter_active_documents,
    set_cached_public_key,
    set_default_replay_service,
    verify_gateway_assertion,
    verify_gateway_or_internal_service,
    verify_internal_service_call,
    verify_knowledge_base_access,
)
from app.services.vector_store import VectorStore


VALID_USER_UUID = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
OTHER_USER_UUID = "8ba91f24-1234-4562-b3fc-2c963f66afa7"

_TEST_PRIVATE_KEY = rsa.generate_private_key(
    public_exponent=65537,
    key_size=2048,
)
_TEST_PUBLIC_KEY = _TEST_PRIVATE_KEY.public_key()
_ROGUE_PRIVATE_KEY = rsa.generate_private_key(
    public_exponent=65537,
    key_size=2048,
)


def create_test_gateway_assertion(
    user_id: str = VALID_USER_UUID,
    secret: str = "test-internal-secret-value-12345",
    expired: bool = False,
    issuer: str = "gateway-service",
    aud: Optional[str] = "rag-service",
    rid: str = "req-12345",
    jti: Optional[Any] = None,
    mth: Optional[Any] = "POST",
    pth: Optional[Any] = "/api/v1/rag/search",
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
    pk = private_key if private_key is not None else _TEST_PRIVATE_KEY
    return jwt.encode(payload_dict, pk, algorithm="RS256", headers={"typ": "JWT"})


class TestRagAuthorizationAndIsolation(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        os.environ["OMRAGUL_TEST_MODE"] = "true"
        self.secret = "test-internal-secret-value-12345"
        settings.internal_service_secret = self.secret
        settings.knowledge_service_url = "http://localhost:8082"
        settings.embedding_dimension = 4
        set_cached_public_key(_TEST_PUBLIC_KEY)
        set_default_replay_service(InMemoryGatewayAssertionReplayService())

        self.test_dir = tempfile.mkdtemp(prefix="omragul_test_routes_")
        self.store = VectorStore(dimension=4, storage_dir=self.test_dir)
        self.client = TestClient(app)
        self.client.__enter__()

    def tearDown(self):
        self.client.__exit__(None, None, None)
        shutil.rmtree(self.test_dir, ignore_errors=True)
        set_cached_public_key(None)

    # -------------------------------------------------------------------------
    # 1. Gateway Assertion Verification Tests
    # -------------------------------------------------------------------------
    def test_verify_gateway_assertion_valid(self):
        """Valid gateway assertion token successfully decodes user subject and claims."""
        token = create_test_gateway_assertion(VALID_USER_UUID, self.secret)
        payload = verify_gateway_assertion(token, self.secret)
        self.assertEqual(payload["sub"], VALID_USER_UUID)
        self.assertEqual(payload["iss"], "gateway-service")
        self.assertEqual(payload["rid"], "req-12345")

    def test_verify_gateway_assertion_missing_token(self):
        """Empty or blank assertion token raises ValueError."""
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion("", self.secret)
        self.assertIn("Missing gateway assertion token", str(ctx.exception))

        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion("   ", self.secret)
        self.assertIn("Missing gateway assertion token", str(ctx.exception))

    def test_verify_gateway_assertion_legacy_hmac_rejected(self):
        """Legacy 2-part HMAC assertion is rejected fail-closed."""
        legacy_token = "payload.signature"
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(legacy_token, self.secret)
        self.assertIn("Legacy HMAC gateway assertions are rejected; RS256 assertions required", str(ctx.exception))

    def test_verify_gateway_assertion_missing_public_key_fails_closed(self):
        """Missing public key raises MissingPublicKeyException and fails closed."""
        set_cached_public_key(None)
        token = create_test_gateway_assertion(VALID_USER_UUID)
        try:
            with self.assertRaises(MissingPublicKeyException):
                verify_gateway_assertion(token)
        finally:
            set_cached_public_key(_TEST_PUBLIC_KEY)

    def test_verify_gateway_assertion_invalid_signature(self):
        """Assertion with incorrect signing key raises ValueError."""
        token = create_test_gateway_assertion(VALID_USER_UUID, private_key=_ROGUE_PRIVATE_KEY)
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, self.secret)
        self.assertIn("signature", str(ctx.exception).lower())

    def test_verify_gateway_assertion_tampered_payload(self):
        """Assertion with tampered payload raises ValueError."""
        token = create_test_gateway_assertion(VALID_USER_UUID, self.secret)
        parts = token.split(".")
        # Tamper payload by replacing subject
        tampered_json = json.dumps({"sub": OTHER_USER_UUID, "iss": "gateway-service", "aud": "rag-service", "jti": str(uuid.uuid4()), "mth": "POST", "pth": "/api/v1/rag/search", "iat": int(time.time()), "exp": int(time.time()) + 60, "rid": "r1"})
        tampered_b64 = base64.urlsafe_b64encode(tampered_json.encode("utf-8")).decode("utf-8").rstrip("=")
        tampered_token = f"{parts[0]}.{tampered_b64}.{parts[2]}"
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(tampered_token, self.secret)
        self.assertTrue("signature" in str(ctx.exception).lower() or "verification failed" in str(ctx.exception).lower())

    def test_verify_gateway_assertion_malformed_token_format(self):
        """Assertion not containing exactly 3 parts (RS256) raises ValueError."""
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion("nodotsinthisstring", self.secret)
        self.assertIn("Malformed gateway assertion structure", str(ctx.exception))

        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion("a.b.c.d", self.secret)
        self.assertIn("Malformed gateway assertion structure", str(ctx.exception))

    def test_verify_gateway_assertion_invalid_base64(self):
        """Assertion payload with invalid base64 encoding raises ValueError."""
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion("header.!!!not-base64!!!.sig", self.secret)
        self.assertTrue("base64" in str(ctx.exception).lower() or "malformed" in str(ctx.exception).lower() or "signature" in str(ctx.exception).lower())

    def test_verify_gateway_assertion_invalid_json(self):
        """Assertion payload with non-JSON bytes raises ValueError."""
        bad_bytes = b"this is not json"
        bad_b64 = base64.urlsafe_b64encode(bad_bytes).decode("utf-8").rstrip("=")
        header_b64 = base64.urlsafe_b64encode(b'{"alg":"RS256","typ":"JWT"}').decode("utf-8").rstrip("=")
        fake_sig = base64.urlsafe_b64encode(b"fakesig" * 30).decode("utf-8").rstrip("=")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(f"{header_b64}.{bad_b64}.{fake_sig}", self.secret)
        self.assertTrue("malformed" in str(ctx.exception).lower() or "json" in str(ctx.exception).lower() or "signature" in str(ctx.exception).lower())

    def test_verify_gateway_assertion_non_object_json(self):
        """Assertion payload containing a JSON array or scalar raises ValueError."""
        bad_json = json.dumps(["an", "array"])
        bad_b64 = base64.urlsafe_b64encode(bad_json.encode("utf-8")).decode("utf-8").rstrip("=")
        header_b64 = base64.urlsafe_b64encode(b'{"alg":"RS256","typ":"JWT"}').decode("utf-8").rstrip("=")
        fake_sig = base64.urlsafe_b64encode(b"fakesig" * 30).decode("utf-8").rstrip("=")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(f"{header_b64}.{bad_b64}.{fake_sig}", self.secret)
        self.assertTrue("json object" in str(ctx.exception).lower() or "malformed" in str(ctx.exception).lower() or "signature" in str(ctx.exception).lower())

    def test_verify_gateway_assertion_invalid_issuer(self):
        """Assertion with unexpected issuer raises ValueError."""
        token = create_test_gateway_assertion(VALID_USER_UUID, issuer="malicious-service")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, self.secret)
        self.assertIn("Invalid gateway assertion issuer", str(ctx.exception))

    def test_verify_gateway_assertion_missing_subject(self):
        """Assertion missing subject claim raises ValueError."""
        payload_dict = {
            "iss": "gateway-service",
            "aud": "rag-service",
            "jti": str(uuid.uuid4()),
            "mth": "POST",
            "pth": "/api/v1/rag/search",
            "iat": int(time.time()),
            "exp": int(time.time()) + 60,
            "rid": "req-1"
        }
        token = jwt.encode(payload_dict, _TEST_PRIVATE_KEY, algorithm="RS256", headers={"typ": "JWT"})
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, self.secret)
        self.assertIn("Missing required claim 'sub'", str(ctx.exception))

    def test_verify_gateway_assertion_invalid_uuid_subject(self):
        """Assertion with non-UUID subject raises ValueError."""
        token = create_test_gateway_assertion("not-a-valid-uuid")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, self.secret)
        self.assertIn("Invalid UUID subject", str(ctx.exception))

    def test_verify_gateway_assertion_missing_request_id(self):
        """Assertion missing request ID raises ValueError."""
        token = create_test_gateway_assertion(VALID_USER_UUID, rid="")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, self.secret)
        self.assertIn("Missing or empty request ID", str(ctx.exception))

    def test_verify_gateway_assertion_invalid_timestamps(self):
        """Assertion with non-integer or boolean timestamps raises ValueError."""
        # Boolean timestamp
        token_bool = create_test_gateway_assertion(VALID_USER_UUID, iat=True)
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token_bool, self.secret)
        self.assertTrue("integer epoch timestamps" in str(ctx.exception) or "timestamp" in str(ctx.exception).lower())

        # String timestamp
        token_str = create_test_gateway_assertion(VALID_USER_UUID, exp="tomorrow")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token_str, self.secret)
        self.assertTrue("integer epoch timestamps" in str(ctx.exception) or "timestamp" in str(ctx.exception).lower())

    def test_verify_gateway_assertion_future_iat(self):
        """Assertion with issued-at more than 10 seconds in the future raises ValueError."""
        future_iat = int(time.time()) + 30
        token = create_test_gateway_assertion(VALID_USER_UUID, iat=future_iat)
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, self.secret)
        self.assertIn("in the future", str(ctx.exception))

    def test_verify_gateway_assertion_expired(self):
        """Expired assertion beyond 10-second skew raises ValueError."""
        token = create_test_gateway_assertion(VALID_USER_UUID, expired=True)
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, self.secret)
        self.assertIn("expired", str(ctx.exception))

    # -------------------------------------------------------------------------
    # 2. Anti-Spoofing & User Identity Extraction Tests
    # -------------------------------------------------------------------------
    def test_anti_spoofing_mismatched_user_id(self):
        """Request where raw X-User-Id differs from assertion sub is rejected with 401."""
        token = create_test_gateway_assertion(VALID_USER_UUID, self.secret)
        mock_request = MagicMock()
        mock_request.headers = {
            "X-Gateway-Assertion": token,
            "X-User-Id": OTHER_USER_UUID
        }
        with self.assertRaises(HTTPException) as ctx:
            extract_and_verify_user_identity(mock_request, self.secret)
        self.assertEqual(ctx.exception.status_code, 401)
        self.assertIn("spoofing", str(ctx.exception.detail).lower())

    def test_anti_spoofing_matching_user_id(self):
        """Request where raw X-User-Id matches assertion sub returns VerifiedIdentity."""
        token = create_test_gateway_assertion(VALID_USER_UUID, self.secret)
        mock_request = MagicMock()
        mock_request.headers = {
            "X-Gateway-Assertion": token,
            "X-User-Id": VALID_USER_UUID
        }
        ident = extract_and_verify_user_identity(mock_request, self.secret)
        self.assertEqual(ident.user_id, VALID_USER_UUID)
        self.assertEqual(ident.assertion_token, token)
        self.assertEqual(ident.request_id, "req-12345")
        self.assertEqual(str(ident), VALID_USER_UUID)

    def test_identity_extraction_internal_secret_rejected(self):
        """Internal service secret alone must NOT authorize user-scoped search (HTTP 401)."""
        mock_request = MagicMock()
        mock_request.headers = {
            "X-Internal-Service-Secret": self.secret
        }
        with self.assertRaises(HTTPException) as ctx:
            extract_and_verify_user_identity(mock_request, self.secret)
        self.assertEqual(ctx.exception.status_code, 401)

    def test_identity_extraction_internal_secret_with_claimed_user_rejected(self):
        """Internal secret + claimed X-User-Id must be rejected with HTTP 401 (fallback removed)."""
        mock_request = MagicMock()
        mock_request.headers = {
            "X-Internal-Service-Secret": self.secret,
            "X-User-Id": VALID_USER_UUID
        }
        with self.assertRaises(HTTPException) as ctx:
            extract_and_verify_user_identity(mock_request, self.secret)
        self.assertEqual(ctx.exception.status_code, 401)

    def test_identity_extraction_missing_credentials(self):
        """Request lacking gateway assertion is rejected with 401."""
        mock_request = MagicMock()
        mock_request.headers = {}
        with self.assertRaises(HTTPException) as ctx:
            extract_and_verify_user_identity(mock_request, self.secret)
        self.assertEqual(ctx.exception.status_code, 401)

    def test_identity_extraction_missing_public_key_fails_closed(self):
        """Missing public key fails closed with HTTP 500."""
        set_cached_public_key(None)
        token = create_test_gateway_assertion(VALID_USER_UUID, self.secret)
        mock_request = MagicMock()
        mock_request.headers = {"X-Gateway-Assertion": token}
        try:
            with self.assertRaises(HTTPException) as ctx:
                extract_and_verify_user_identity(mock_request)
            self.assertEqual(ctx.exception.status_code, 500)
        finally:
            set_cached_public_key(_TEST_PUBLIC_KEY)

    # -------------------------------------------------------------------------
    # 3. Query Embedding Endpoint Authentication Tests
    # -------------------------------------------------------------------------
    def test_verify_gateway_or_internal_service_no_credentials(self):
        """Query embedding request with no credentials is rejected with HTTP 401."""
        mock_request = MagicMock()
        mock_request.headers = {}
        with self.assertRaises(HTTPException) as ctx:
            verify_gateway_or_internal_service(mock_request, self.secret)
        self.assertEqual(ctx.exception.status_code, 401)

    def test_verify_gateway_or_internal_service_raw_user_id_rejected(self):
        """Query embedding request with only raw X-User-Id is rejected with HTTP 401."""
        mock_request = MagicMock()
        mock_request.headers = {"X-User-Id": VALID_USER_UUID}
        with self.assertRaises(HTTPException) as ctx:
            verify_gateway_or_internal_service(mock_request, self.secret)
        self.assertEqual(ctx.exception.status_code, 401)

    def test_verify_gateway_or_internal_service_valid_gateway_assertion(self):
        """Query embedding with valid Gateway Assertion is accepted."""
        token = create_test_gateway_assertion(VALID_USER_UUID, self.secret)
        mock_request = MagicMock()
        mock_request.headers = {"X-Gateway-Assertion": token}
        caller = verify_gateway_or_internal_service(mock_request, self.secret)
        self.assertEqual(caller, f"gateway:{VALID_USER_UUID}")

    def test_verify_gateway_or_internal_service_valid_internal_secret(self):
        """Query embedding with valid Internal Service Secret is accepted."""
        mock_request = MagicMock()
        mock_request.headers = {
            "X-Internal-Service-Secret": self.secret,
            "X-Internal-Caller-Service": "agent-service"
        }
        caller = verify_gateway_or_internal_service(mock_request, self.secret)
        self.assertEqual(caller, "internal:agent-service")

    def test_verify_gateway_or_internal_service_missing_secret_fails_closed(self):
        """Query embedding with unconfigured server secret fails closed with HTTP 500."""
        mock_request = MagicMock()
        mock_request.headers = {"X-Internal-Service-Secret": "any"}
        with self.assertRaises(HTTPException) as ctx:
            verify_gateway_or_internal_service(mock_request, "")
        self.assertEqual(ctx.exception.status_code, 500)

    # -------------------------------------------------------------------------
    # 4. Knowledge Base Access Verification & Propagation Tests
    # -------------------------------------------------------------------------
    async def test_knowledge_base_access_forwards_gateway_assertion(self):
        """verify_knowledge_base_access forwards exact Gateway Assertion and caller context."""
        token = create_test_gateway_assertion(VALID_USER_UUID, self.secret)
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_client = AsyncMock()
        mock_client.get = AsyncMock(return_value=mock_resp)

        has_access = await verify_knowledge_base_access(
            user_id=VALID_USER_UUID,
            knowledge_base_id="kb-1",
            knowledge_service_url="http://localhost:8082",
            internal_secret=self.secret,
            client=mock_client,
            gateway_assertion=token,
            request_id="req-custom-999"
        )
        self.assertTrue(has_access)

        mock_client.get.assert_called_once()
        url, kwargs = mock_client.get.call_args
        headers = kwargs.get("headers", {})
        self.assertEqual(headers["X-Gateway-Assertion"], token)
        self.assertEqual(headers["X-User-Id"], VALID_USER_UUID)
        self.assertEqual(headers["X-Request-Id"], "req-custom-999")
        # FINDING-3.7-INT-01: User authorization request must NOT carry internal service secret
        self.assertNotIn("X-Internal-Secret", headers)
        self.assertNotIn("X-Internal-Service-Secret", headers)
        self.assertNotIn("X-Internal-Caller-Service", headers)

    async def test_knowledge_base_access_missing_assertion_fails_closed(self):
        """Missing Gateway Assertion fails closed without making an HTTP call."""
        mock_client = AsyncMock()
        has_access = await verify_knowledge_base_access(
            user_id=VALID_USER_UUID,
            knowledge_base_id="kb-1",
            knowledge_service_url="http://localhost:8082",
            internal_secret=self.secret,
            client=mock_client,
            gateway_assertion=None
        )
        self.assertFalse(has_access)
        mock_client.get.assert_not_called()

    async def test_knowledge_base_access_forbidden(self):
        """403 response from Knowledge Service returns False."""
        token = create_test_gateway_assertion(VALID_USER_UUID, self.secret)
        mock_resp = MagicMock()
        mock_resp.status_code = 403
        mock_client = AsyncMock()
        mock_client.get = AsyncMock(return_value=mock_resp)

        has_access = await verify_knowledge_base_access(
            user_id=VALID_USER_UUID,
            knowledge_base_id="kb-forbidden",
            knowledge_service_url="http://localhost:8082",
            internal_secret=self.secret,
            client=mock_client,
            gateway_assertion=token
        )
        self.assertFalse(has_access)

    async def test_knowledge_base_access_not_found(self):
        """404 response from Knowledge Service returns False."""
        token = create_test_gateway_assertion(VALID_USER_UUID, self.secret)
        mock_resp = MagicMock()
        mock_resp.status_code = 404
        mock_client = AsyncMock()
        mock_client.get = AsyncMock(return_value=mock_resp)

        has_access = await verify_knowledge_base_access(
            user_id=VALID_USER_UUID,
            knowledge_base_id="kb-404",
            knowledge_service_url="http://localhost:8082",
            internal_secret=self.secret,
            client=mock_client,
            gateway_assertion=token
        )
        self.assertFalse(has_access)

    async def test_knowledge_base_access_fail_closed_on_error(self):
        """Knowledge Service 5xx or unreachability fails closed with HTTP 502."""
        token = create_test_gateway_assertion(VALID_USER_UUID, self.secret)
        mock_resp = MagicMock()
        mock_resp.status_code = 500
        mock_client = AsyncMock()
        mock_client.get = AsyncMock(return_value=mock_resp)

        with self.assertRaises(HTTPException) as ctx:
            await verify_knowledge_base_access(
                user_id=VALID_USER_UUID,
                knowledge_base_id="kb-err",
                knowledge_service_url="http://localhost:8082",
                internal_secret=self.secret,
                client=mock_client,
                gateway_assertion=token
            )
        self.assertEqual(ctx.exception.status_code, 502)

    # -------------------------------------------------------------------------
    # 5. Route-level HTTP Client Tests (/query/embedding and /search)
    # -------------------------------------------------------------------------
    @patch("app.services.embedding_client.EmbeddingClient.generate_embedding")
    def test_query_embedding_route_unauthenticated(self, mock_gen):
        """POST /api/v1/rag/query/embedding without credentials returns HTTP 401."""
        response = self.client.post(
            "/api/v1/rag/query/embedding",
            json={"query": "test query"}
        )
        self.assertEqual(response.status_code, 401)
        mock_gen.assert_not_called()

    @patch("app.services.embedding_client.EmbeddingClient.generate_embedding")
    def test_query_embedding_route_with_gateway_assertion(self, mock_gen):
        """POST /api/v1/rag/query/embedding with Gateway Assertion succeeds."""
        mock_gen.return_value = [0.1, 0.2, 0.3, 0.4]
        token = create_test_gateway_assertion(VALID_USER_UUID, self.secret, pth="/api/v1/rag/query/embedding")

        response = self.client.post(
            "/api/v1/rag/query/embedding",
            json={"query": "test query"},
            headers={"X-Gateway-Assertion": token}
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["dimensions"], 4)
        mock_gen.assert_called_once_with("test query", client=ANY)

    @patch("app.services.embedding_client.EmbeddingClient.generate_embedding")
    def test_query_embedding_route_with_internal_secret(self, mock_gen):
        """POST /api/v1/rag/query/embedding with internal service secret succeeds."""
        mock_gen.return_value = [0.5, 0.6, 0.7, 0.8]

        response = self.client.post(
            "/api/v1/rag/query/embedding",
            json={"query": "internal search query"},
            headers={"X-Internal-Service-Secret": self.secret}
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["dimensions"], 4)
        mock_gen.assert_called_once_with("internal search query", client=ANY)

    def test_query_embedding_route_empty_query(self):
        """POST /api/v1/rag/query/embedding with empty query returns HTTP 400."""
        token = create_test_gateway_assertion(VALID_USER_UUID, self.secret, pth="/api/v1/rag/query/embedding")
        response = self.client.post(
            "/api/v1/rag/query/embedding",
            json={"query": "   "},
            headers={"X-Gateway-Assertion": token}
        )
        self.assertEqual(response.status_code, 400)

    def test_query_embedding_route_path_mismatch_rejected(self):
        """POST /api/v1/rag/query/embedding with token bound to /api/v1/rag/search is rejected with 401."""
        token = create_test_gateway_assertion(VALID_USER_UUID, self.secret, pth="/api/v1/rag/search")
        response = self.client.post(
            "/api/v1/rag/query/embedding",
            json={"query": "test query"},
            headers={"X-Gateway-Assertion": token}
        )
        self.assertEqual(response.status_code, 401)
        self.assertIn("path", response.json()["detail"].lower())

    def test_search_route_unauthenticated(self):
        """POST /api/v1/rag/search without assertion returns HTTP 401."""
        response = self.client.post(
            "/api/v1/rag/search",
            json={"query": "test query", "knowledge_base_id": "kb-1", "top_k": 5}
        )
        self.assertEqual(response.status_code, 401)

    def test_search_route_internal_secret_only_rejected(self):
        """POST /api/v1/rag/search with only internal service secret returns HTTP 401."""
        response = self.client.post(
            "/api/v1/rag/search",
            json={"query": "test query", "knowledge_base_id": "kb-1", "top_k": 5},
            headers={"X-Internal-Service-Secret": self.secret}
        )
        self.assertEqual(response.status_code, 401)

    def test_search_route_internal_secret_with_claimed_user_rejected(self):
        """POST /api/v1/rag/search with internal secret + claimed X-User-Id returns HTTP 401."""
        response = self.client.post(
            "/api/v1/rag/search",
            json={"query": "test query", "knowledge_base_id": "kb-1", "top_k": 5},
            headers={
                "X-Internal-Service-Secret": self.secret,
                "X-User-Id": VALID_USER_UUID
            }
        )
        self.assertEqual(response.status_code, 401)

    def test_search_route_expired_assertion_rejected(self):
        """POST /api/v1/rag/search with expired assertion returns HTTP 401."""
        token = create_test_gateway_assertion(VALID_USER_UUID, self.secret, expired=True)
        response = self.client.post(
            "/api/v1/rag/search",
            json={"query": "test query", "knowledge_base_id": "kb-1", "top_k": 5},
            headers={"X-Gateway-Assertion": token}
        )
        self.assertEqual(response.status_code, 401)

    def test_search_route_mismatched_user_id_rejected(self):
        """POST /api/v1/rag/search with mismatched X-User-Id returns HTTP 401."""
        token = create_test_gateway_assertion(VALID_USER_UUID, self.secret)
        response = self.client.post(
            "/api/v1/rag/search",
            json={"query": "test query", "knowledge_base_id": "kb-1", "top_k": 5},
            headers={
                "X-Gateway-Assertion": token,
                "X-User-Id": OTHER_USER_UUID
            }
        )
        self.assertEqual(response.status_code, 401)

    @patch("app.api.routes.filter_active_documents")
    @patch("app.api.routes.vector_store.search")
    @patch("app.api.routes.verify_knowledge_base_access")
    @patch("app.services.embedding_client.EmbeddingClient.generate_embedding")
    def test_search_route_valid_assertion_succeeds(self, mock_embed, mock_kb_access, mock_search, mock_filter):
        """POST /api/v1/rag/search with valid assertion and authorized KB succeeds."""
        mock_embed.return_value = [0.1] * 1024
        mock_kb_access.return_value = True
        mock_filter.return_value = ["doc-test-1"]
        mock_search.return_value = [
            {"score": 0.95, "document": {"document_id": "doc-test-1", "text": "Sample text for search"}}
        ]

        token = create_test_gateway_assertion(VALID_USER_UUID, self.secret)
        response = self.client.post(
            "/api/v1/rag/search",
            json={"query": "sample search", "knowledge_base_id": "kb-valid-1", "top_k": 5},
            headers={"X-Gateway-Assertion": token}
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertIn("results", data)
        self.assertEqual(len(data["results"]), 1)
        mock_kb_access.assert_called_once()
        mock_search.assert_called_once()
        mock_filter.assert_called_once()

    @patch("app.services.embedding_client.EmbeddingClient.generate_embedding")
    @patch("app.api.routes.verify_knowledge_base_access")
    def test_search_route_unauthorized_kb_forbidden(self, mock_kb_access, mock_embed):
        """POST /api/v1/rag/search when Knowledge Service denies access returns HTTP 403 and never generates embeddings."""
        mock_kb_access.return_value = False
        token = create_test_gateway_assertion(VALID_USER_UUID, self.secret)
        response = self.client.post(
            "/api/v1/rag/search",
            json={"query": "sample search", "knowledge_base_id": "kb-denied", "top_k": 5},
            headers={"X-Gateway-Assertion": token}
        )
        self.assertEqual(response.status_code, 403)
        mock_embed.assert_not_called()

    @patch("app.services.embedding_client.EmbeddingClient.generate_embedding")
    @patch("app.api.routes.verify_knowledge_base_access")
    def test_search_route_knowledge_service_outage_returns_502(self, mock_kb_access, mock_embed):
        """POST /api/v1/rag/search when Knowledge Service is unreachable returns HTTP 502 and never generates embeddings."""
        mock_kb_access.side_effect = HTTPException(status_code=502, detail="Authorization service unavailable")
        token = create_test_gateway_assertion(VALID_USER_UUID, self.secret)
        response = self.client.post(
            "/api/v1/rag/search",
            json={"query": "sample search", "knowledge_base_id": "kb-unreachable", "top_k": 5},
            headers={"X-Gateway-Assertion": token}
        )
        self.assertEqual(response.status_code, 502)
        mock_embed.assert_not_called()

    @patch("app.services.embedding_client.EmbeddingClient.generate_embedding")
    def test_search_route_unauthenticated_never_calls_embedding(self, mock_embed):
        """POST /api/v1/rag/search without assertion rejects with 401 and never generates embeddings."""
        response = self.client.post(
            "/api/v1/rag/search",
            json={"query": "test query", "knowledge_base_id": "kb-1", "top_k": 5}
        )
        self.assertEqual(response.status_code, 401)
        mock_embed.assert_not_called()

    # -------------------------------------------------------------------------
    # 6. Service-to-Service Internal Authentication & Vector Store Isolation
    # -------------------------------------------------------------------------
    def test_verify_internal_service_call_valid(self):
        """Internal service secret is accepted and returns caller service."""
        mock_request = MagicMock()
        mock_request.headers = {
            "X-Internal-Service-Secret": self.secret,
            "X-Internal-Caller-Service": "knowledge-service"
        }
        caller = verify_internal_service_call(mock_request, self.secret)
        self.assertEqual(caller, "knowledge-service")

    def test_verify_internal_service_call_missing_secret(self):
        """Missing internal service secret is rejected with 401."""
        mock_request = MagicMock()
        mock_request.headers = {}
        with self.assertRaises(HTTPException) as ctx:
            verify_internal_service_call(mock_request, self.secret)
        self.assertEqual(ctx.exception.status_code, 401)

    def test_verify_internal_service_call_invalid_secret(self):
        """Incorrect internal service secret is rejected with 401."""
        mock_request = MagicMock()
        mock_request.headers = {"X-Internal-Service-Secret": "wrong-secret"}
        with self.assertRaises(HTTPException) as ctx:
            verify_internal_service_call(mock_request, self.secret)
        self.assertEqual(ctx.exception.status_code, 401)

    def test_verify_internal_service_call_user_assertion_cannot_bypass(self):
        """User gateway assertion without internal secret cannot access internal service endpoints."""
        token = create_test_gateway_assertion(VALID_USER_UUID, self.secret)
        mock_request = MagicMock()
        mock_request.headers = {"X-Gateway-Assertion": token}
        with self.assertRaises(HTTPException) as ctx:
            verify_internal_service_call(mock_request, self.secret)
        self.assertEqual(ctx.exception.status_code, 401)

    def test_scoped_retrieval_and_document_deletion(self):
        """Scoped search retrieves only target KB; deletion purges vectors immediately."""
        self.store.add(
            embeddings=[[1.0, 0.0, 0.0, 0.0], [0.0, 1.0, 0.0, 0.0]],
            documents=[
                {"document_id": "doc-alpha-1", "text": "Alpha chunk 1"},
                {"document_id": "doc-alpha-2", "text": "Alpha chunk 2"}
            ],
            knowledge_base_id="kb-alpha"
        )

        self.store.add(
            embeddings=[[0.0, 0.0, 1.0, 0.0]],
            documents=[{"document_id": "doc-beta-1", "text": "Beta secret chunk"}],
            knowledge_base_id="kb-beta"
        )

        results = self.store.search(
            query_embedding=[1.0, 0.0, 0.0, 0.0],
            knowledge_base_id="kb-alpha",
            top_k=5
        )
        self.assertEqual(len(results), 2)
        doc_ids = [r["document"]["document_id"] for r in results]
        self.assertIn("doc-alpha-1", doc_ids)
        self.assertNotIn("doc-beta-1", doc_ids)

        deleted = self.store.delete_document("doc-alpha-1", knowledge_base_id="kb-alpha")
        self.assertEqual(deleted, 1)

        post_del_results = self.store.search(
            query_embedding=[1.0, 0.0, 0.0, 0.0],
            knowledge_base_id="kb-alpha",
            top_k=5
        )
        self.assertEqual(len(post_del_results), 1)
        self.assertEqual(post_del_results[0]["document"]["document_id"], "doc-alpha-2")

    async def test_filter_active_documents_filters_deleted(self):
        """Soft-deleted documents are authoritatively filtered out."""
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = {
            "knowledgeBaseId": "kb-1",
            "activeDocumentIds": ["doc-active-1"]
        }
        mock_client = AsyncMock()
        mock_client.post = AsyncMock(return_value=mock_resp)

        active = await filter_active_documents(
            knowledge_base_id="kb-1",
            candidate_doc_ids=["doc-active-1", "doc-deleted-2"],
            knowledge_service_url="http://localhost:8082",
            internal_secret=self.secret,
            client=mock_client
        )
        self.assertIn("doc-active-1", active)
        self.assertNotIn("doc-deleted-2", active)
        mock_client.post.assert_called_once()
        url, kwargs = mock_client.post.call_args
        headers = kwargs.get("headers", {})
        self.assertEqual(headers.get("X-Internal-Secret"), self.secret)
        self.assertEqual(headers.get("X-Internal-Service-Secret"), self.secret)
        self.assertEqual(headers.get("X-Internal-Caller-Service"), "rag-service")

    async def test_filter_active_documents_fails_closed_on_error(self):
        """Knowledge Service 5xx during document status check fails closed with HTTP 502."""
        mock_resp = MagicMock()
        mock_resp.status_code = 500
        mock_client = AsyncMock()
        mock_client.post = AsyncMock(return_value=mock_resp)

        with self.assertRaises(HTTPException) as ctx:
            await filter_active_documents(
                knowledge_base_id="kb-1",
                candidate_doc_ids=["doc-1"],
                knowledge_service_url="http://localhost:8082",
                internal_secret=self.secret,
                client=mock_client
            )
        self.assertEqual(ctx.exception.status_code, 502)


if __name__ == "__main__":
    unittest.main()
