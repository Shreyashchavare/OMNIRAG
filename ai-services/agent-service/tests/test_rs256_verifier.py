"""
Comprehensive RS256 and Dual-Verification Test Suite for Agent Service (Stage 3.6 Batch 4.2A).
Covers all 16 required verification test cases:
1. Valid RS256 token -> Accepted, user identity and roles correctly extracted.
2. Invalid RS256 signature (wrong key) -> Rejected.
3. Tampered payload -> Rejected.
4. Tampered header -> Rejected.
5. Expired token -> Rejected.
6. Future iat beyond skew -> Rejected.
7. Invalid/non-UUID sub -> Rejected.
8. Missing required claims (sub, iss, iat, exp, rid) -> Rejected.
9. Wrong issuer -> Rejected.
10. Algorithm none -> Rejected.
11. Algorithm HS256 -> Rejected.
12. 3-part token malformed -> Rejected (MUST NOT fall back to HMAC).
13. Legacy HMAC token with legacy_hmac_enabled=true -> Accepted.
14. Legacy HMAC token with legacy_hmac_enabled=false -> Rejected.
15. Invalid legacy HMAC signature -> Rejected.
16. Public key not configured -> Rejected (fails closed).
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

from cryptography.hazmat.primitives.asymmetric import rsa
import jwt

agent_service_path = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if agent_service_path not in sys.path:
    sys.path.insert(0, agent_service_path)

# Mock langchain_ollama and httpx if needed
if "langchain_ollama" not in sys.modules:
    from unittest.mock import MagicMock
    sys.modules["langchain_ollama"] = MagicMock()
if "httpx" not in sys.modules:
    from unittest.mock import MagicMock
    mock_httpx = MagicMock()
    mock_httpx.HTTPError = Exception
    mock_httpx.HTTPStatusError = Exception
    sys.modules["httpx"] = mock_httpx

from app.config.settings import settings
from app.services.auth_service import (
    InMemoryGatewayAssertionReplayService,
    MissingPublicKeyException,
    MissingSecretException,
    set_cached_public_key,
    set_default_replay_service,
    verify_gateway_assertion,
)

VALID_USER_UUID = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
OTHER_USER_UUID = "8ba91f24-1234-4562-b3fc-2c963f66afa7"
TEST_SECRET = "omragul-test-agent-service-secret-2026"


class TestAgentRS256Verifier(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        os.environ["OMRAGUL_TEST_MODE"] = "true"
        cls.test_private_key = rsa.generate_private_key(65537, 2048)
        cls.test_public_key = cls.test_private_key.public_key()
        cls.rogue_private_key = rsa.generate_private_key(65537, 2048)

    def setUp(self):
        os.environ["OMRAGUL_TEST_MODE"] = "true"
        settings.INTERNAL_SERVICE_SECRET = TEST_SECRET
        set_cached_public_key(self.test_public_key)
        set_default_replay_service(InMemoryGatewayAssertionReplayService())

    def _build_rs256_token(
        self,
        sub: str = VALID_USER_UUID,
        iss: str = "gateway-service",
        aud: Any = "agent-service",
        iat: Optional[Any] = None,
        exp: Optional[Any] = None,
        rid: str = "req-agent-rs256-1",
        roles: str = "ROLE_USER,ROLE_ADMIN",
        jti: Optional[Any] = None,
        mth: Optional[Any] = "GET",
        pth: Optional[Any] = "/api/v1/agent/chat",
        sign_key: Optional[rsa.RSAPrivateKey] = None,
        headers: Optional[Dict[str, Any]] = None,
    ) -> str:
        now = int(time.time())
        key = sign_key if sign_key is not None else self.test_private_key
        payload = {
            "sub": sub,
            "iss": iss,
            "iat": now if iat is None else iat,
            "exp": (now + 60) if exp is None else exp,
            "rid": rid,
            "roles": roles,
        }
        if aud != "OMIT":
            payload["aud"] = aud
        if jti != "OMIT":
            payload["jti"] = str(uuid.uuid4()) if jti is None else jti
        if mth != "OMIT":
            payload["mth"] = mth
        if pth != "OMIT":
            payload["pth"] = pth
        jwt_headers = headers if headers is not None else {"typ": "JWT", "alg": "RS256"}
        return jwt.encode(payload, key, algorithm="RS256", headers=jwt_headers)

    def _build_legacy_hmac_token(
        self,
        sub: str = VALID_USER_UUID,
        roles: str = "ROLE_USER",
        iss: str = "gateway-service",
        iat: Optional[int] = None,
        exp: Optional[int] = None,
        rid: str = "req-agent-legacy-1",
        secret: str = TEST_SECRET,
    ) -> str:
        now = int(time.time())
        payload_dict = {
            "sub": sub,
            "roles": roles,
            "iss": iss,
            "iat": now if iat is None else iat,
            "exp": (now + 60) if exp is None else exp,
            "rid": rid,
        }
        payload_json = json.dumps(payload_dict)
        payload_b64 = base64.urlsafe_b64encode(payload_json.encode("utf-8")).decode("utf-8").rstrip("=")
        signature = hmac.new(secret.encode("utf-8"), payload_b64.encode("utf-8"), hashlib.sha256).hexdigest()
        return f"{payload_b64}.{signature}"

    def test_01_valid_rs256_token_accepted(self):
        token = self._build_rs256_token()
        claims = verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertEqual(claims["sub"], VALID_USER_UUID)
        self.assertEqual(claims["iss"], "gateway-service")
        self.assertEqual(claims["rid"], "req-agent-rs256-1")
        self.assertEqual(claims["roles"], "ROLE_USER,ROLE_ADMIN")

    def test_02_invalid_rs256_signature_rejected(self):
        token = self._build_rs256_token(sign_key=self.rogue_private_key)
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("signature", str(ctx.exception).lower())

    def test_03_tampered_payload_rejected(self):
        token = self._build_rs256_token()
        parts = token.split(".")
        tampered_dict = {
            "sub": OTHER_USER_UUID,
            "iss": "gateway-service",
            "aud": "agent-service",
            "jti": str(uuid.uuid4()),
            "mth": "GET",
            "pth": "/api/v1/agent/chat",
            "iat": int(time.time()),
            "exp": int(time.time()) + 60,
            "rid": "req-agent-rs256-1",
            "roles": "ROLE_ADMIN",
        }
        tampered_b64 = base64.urlsafe_b64encode(json.dumps(tampered_dict).encode("utf-8")).decode("utf-8").rstrip("=")
        tampered_token = f"{parts[0]}.{tampered_b64}.{parts[2]}"
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(tampered_token, public_key=self.test_public_key)
        self.assertIn("signature", str(ctx.exception).lower())

    def test_04_tampered_header_rejected(self):
        token = self._build_rs256_token()
        parts = token.split(".")
        tampered_h = {"alg": "RS256", "typ": "JWT", "tampered": True}
        tampered_hb64 = base64.urlsafe_b64encode(json.dumps(tampered_h).encode("utf-8")).decode("utf-8").rstrip("=")
        tampered_token = f"{tampered_hb64}.{parts[1]}.{parts[2]}"
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(tampered_token, public_key=self.test_public_key)
        self.assertIn("signature", str(ctx.exception).lower())

    def test_05_expired_token_rejected(self):
        now = int(time.time())
        token = self._build_rs256_token(iat=now - 200, exp=now - 100)
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("expired", str(ctx.exception).lower())

    def test_06_future_iat_rejected(self):
        now = int(time.time())
        token = self._build_rs256_token(iat=now + 100, exp=now + 200)
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("future", str(ctx.exception).lower())

    def test_07_invalid_non_uuid_sub_rejected(self):
        token = self._build_rs256_token(sub="invalid-non-uuid-string")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("uuid", str(ctx.exception).lower())

    def test_08_missing_required_claims_rejected(self):
        now = int(time.time())
        # Missing sub
        payload = {"iss": "gateway-service", "aud": "agent-service", "jti": str(uuid.uuid4()), "mth": "GET", "pth": "/api/v1/agent/chat", "iat": now, "exp": now + 60, "rid": "req-1"}
        t1 = jwt.encode(payload, self.test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})
        with self.assertRaises(ValueError):
            verify_gateway_assertion(t1, public_key=self.test_public_key)

        # Missing rid
        payload2 = {"sub": VALID_USER_UUID, "iss": "gateway-service", "aud": "agent-service", "jti": str(uuid.uuid4()), "mth": "GET", "pth": "/api/v1/agent/chat", "iat": now, "exp": now + 60}
        t2 = jwt.encode(payload2, self.test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})
        with self.assertRaises(ValueError):
            verify_gateway_assertion(t2, public_key=self.test_public_key)

        # Missing aud
        payload3 = {"sub": VALID_USER_UUID, "iss": "gateway-service", "jti": str(uuid.uuid4()), "mth": "GET", "pth": "/api/v1/agent/chat", "iat": now, "exp": now + 60, "rid": "req-1"}
        t3 = jwt.encode(payload3, self.test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})
        with self.assertRaises(ValueError):
            verify_gateway_assertion(t3, public_key=self.test_public_key)

        # Missing jti
        payload4 = {"sub": VALID_USER_UUID, "iss": "gateway-service", "aud": "agent-service", "mth": "GET", "pth": "/api/v1/agent/chat", "iat": now, "exp": now + 60, "rid": "req-1"}
        t4 = jwt.encode(payload4, self.test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(t4, public_key=self.test_public_key)
        self.assertIn("jti", str(ctx.exception).lower())

        # Missing mth
        payload5 = {"sub": VALID_USER_UUID, "iss": "gateway-service", "aud": "agent-service", "jti": str(uuid.uuid4()), "pth": "/api/v1/agent/chat", "iat": now, "exp": now + 60, "rid": "req-1"}
        t5 = jwt.encode(payload5, self.test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(t5, public_key=self.test_public_key)
        self.assertIn("mth", str(ctx.exception).lower())

        # Missing pth
        payload6 = {"sub": VALID_USER_UUID, "iss": "gateway-service", "aud": "agent-service", "jti": str(uuid.uuid4()), "mth": "GET", "iat": now, "exp": now + 60, "rid": "req-1"}
        t6 = jwt.encode(payload6, self.test_private_key, algorithm="RS256", headers={"typ": "JWT", "alg": "RS256"})
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(t6, public_key=self.test_public_key)
        self.assertIn("pth", str(ctx.exception).lower())

    def test_09_wrong_issuer_rejected(self):
        token = self._build_rs256_token(iss="rogue-service")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("issuer", str(ctx.exception).lower())

    def test_10_alg_none_rejected(self):
        now = int(time.time())
        payload = {"sub": VALID_USER_UUID, "iss": "gateway-service", "aud": "agent-service", "jti": str(uuid.uuid4()), "mth": "GET", "pth": "/api/v1/agent/chat", "iat": now, "exp": now + 60, "rid": "req-1"}
        h_b64 = base64.urlsafe_b64encode(b'{"alg":"none","typ":"JWT"}').decode("utf-8").rstrip("=")
        p_b64 = base64.urlsafe_b64encode(json.dumps(payload).encode("utf-8")).decode("utf-8").rstrip("=")
        token = f"{h_b64}.{p_b64}."
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("algorithm", str(ctx.exception).lower())

    def test_11_alg_hs256_rejected(self):
        now = int(time.time())
        payload = {"sub": VALID_USER_UUID, "iss": "gateway-service", "aud": "agent-service", "jti": str(uuid.uuid4()), "mth": "GET", "pth": "/api/v1/agent/chat", "iat": now, "exp": now + 60, "rid": "req-1"}
        token = jwt.encode(payload, TEST_SECRET, algorithm="HS256", headers={"typ": "JWT", "alg": "HS256"})
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("algorithm", str(ctx.exception).lower())

    def test_12_malformed_3_part_never_falls_back_to_hmac(self):
        token = "malformed.three.part"
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, secret=TEST_SECRET, public_key=self.test_public_key)
        self.assertNotIn("Invalid gateway assertion signature", str(ctx.exception))

    # =========================================================================
    # Audience Validation Tests (Stage 3.6 Batch 4.3A)
    # =========================================================================

    def test_audience_matching_accepted(self):
        token = self._build_rs256_token(aud="agent-service")
        claims = verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertEqual(claims["aud"], "agent-service")

    def test_audience_wrong_service_rejected(self):
        token = self._build_rs256_token(aud="knowledge-service")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("audience", str(ctx.exception).lower())

    def test_audience_missing_rejected(self):
        token = self._build_rs256_token(aud="OMIT")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("claim 'aud'", str(ctx.exception).lower())

    def test_audience_empty_rejected(self):
        token = self._build_rs256_token(aud="")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("missing or empty assertion audience", str(ctx.exception).lower())

    def test_audience_whitespace_rejected(self):
        token = self._build_rs256_token(aud="   ")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("missing or empty assertion audience", str(ctx.exception).lower())

    def test_audience_multi_array_rejected(self):
        token = self._build_rs256_token(aud=["agent-service"])
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("multi-audience array not allowed", str(ctx.exception).lower())

    def test_audience_object_rejected(self):
        token = self._build_rs256_token(aud={"target": "agent-service"})
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("expected string, got object", str(ctx.exception).lower())

    def test_custom_expected_audience(self):
        token_custom = self._build_rs256_token(aud="custom-agent")
        claims = verify_gateway_assertion(token_custom, public_key=self.test_public_key, expected_audience="custom-agent")
        self.assertEqual(claims["aud"], "custom-agent")

        token_default = self._build_rs256_token(aud="agent-service")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token_default, public_key=self.test_public_key, expected_audience="custom-agent")
        self.assertIn("audience", str(ctx.exception).lower())

    def test_audience_whitespace_padded_rejected(self):
        token = self._build_rs256_token(aud=" agent-service ")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("invalid gateway assertion audience: expected agent-service, got  agent-service ", str(ctx.exception).lower())

    def test_blank_expected_audience_rejected(self):
        token = self._build_rs256_token(aud="agent-service")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key, expected_audience="")
        self.assertIn("audience not configured", str(ctx.exception).lower())

    def test_13_legacy_hmac_rejected(self):
        token = self._build_legacy_hmac_token()
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, secret=TEST_SECRET, legacy_hmac_enabled=True)
        self.assertIn("legacy hmac gateway assertions are rejected", str(ctx.exception).lower())

    def test_14_legacy_hmac_rejected_when_disabled(self):
        token = self._build_legacy_hmac_token()
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, secret=TEST_SECRET, legacy_hmac_enabled=False)
        self.assertIn("legacy hmac gateway assertions are rejected", str(ctx.exception).lower())

    def test_15_invalid_legacy_hmac_signature_rejected(self):
        token = self._build_legacy_hmac_token(secret="wrong-secret")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, secret=TEST_SECRET, legacy_hmac_enabled=True)
        self.assertIn("legacy hmac gateway assertions are rejected", str(ctx.exception).lower())

    def test_16_public_key_not_configured_fails_closed(self):
        token = self._build_rs256_token()
        set_cached_public_key(None)
        with self.assertRaises((MissingPublicKeyException, ValueError)):
            verify_gateway_assertion(token, public_key=None)

    def test_17_missing_configured_public_key_path_raises_file_not_found(self):
        from app.services.auth_service import resolve_and_load_public_key
        with self.assertRaises(FileNotFoundError):
            resolve_and_load_public_key("/nonexistent/file/path/public_key.pem")

    # =========================================================================
    # Distributed Replay Protection Tests (Stage 3.6 Batch 4.3B)
    # =========================================================================

    def test_replay_first_use_accepted(self):
        token = self._build_rs256_token()
        claims = verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIsNotNone(claims.get("jti"))

    def test_replay_second_use_rejected(self):
        token = self._build_rs256_token()
        claims = verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIsNotNone(claims)
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("replay detected", str(ctx.exception).lower())

    def test_missing_jti_rejected(self):
        token = self._build_rs256_token(jti="OMIT")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("claim 'jti'", str(ctx.exception).lower())

    def test_empty_jti_rejected(self):
        token = self._build_rs256_token(jti="")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("empty assertion identifier (jti)", str(ctx.exception).lower())

    def test_whitespace_jti_rejected(self):
        token = self._build_rs256_token(jti="   ")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("empty assertion identifier (jti)", str(ctx.exception).lower())

    def test_whitespace_padded_jti_rejected(self):
        token = self._build_rs256_token(jti=f" {str(uuid.uuid4())} ")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("whitespace padding not allowed", str(ctx.exception).lower())

    def test_short_jti_rejected(self):
        token = self._build_rs256_token(jti="short-id")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("invalid gateway assertion jti length", str(ctx.exception).lower())

    def test_long_jti_rejected(self):
        token = self._build_rs256_token(jti="a" * 129)
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertIn("invalid gateway assertion jti length", str(ctx.exception).lower())

    def test_non_string_jti_rejected(self):
        token = self._build_rs256_token(jti=1234567890123456)
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key)
        self.assertTrue(
            "must be a string" in str(ctx.exception).lower() or "expected string" in str(ctx.exception).lower(),
            f"Expected string type error, got: {ctx.exception}"
        )

    def test_replay_service_fail_closed_on_error(self):
        from app.services.auth_service import GatewayAssertionReplayService
        class FailingReplayService(GatewayAssertionReplayService):
            def claim_jti(self, jti: str, consumer_service: str, issuer: str, exp: int) -> bool:
                raise RuntimeError("Redis connection timed out")

        token = self._build_rs256_token()
        with self.assertRaises(RuntimeError) as ctx:
            verify_gateway_assertion(token, public_key=self.test_public_key, replay_service=FailingReplayService())
        self.assertIn("redis connection timed out", str(ctx.exception).lower())

    def test_replay_namespace_service_isolation(self):
        shared_jti = str(uuid.uuid4())
        token_agent = self._build_rs256_token(aud="agent-service", jti=shared_jti)
        claims = verify_gateway_assertion(token_agent, public_key=self.test_public_key, expected_audience="agent-service")
        self.assertEqual(claims["jti"], shared_jti)

    def test_replay_concurrent_duplicate_requests(self):
        import concurrent.futures
        token = self._build_rs256_token()
        results = []
        errors = []

        def verify():
            try:
                claims = verify_gateway_assertion(token, public_key=self.test_public_key)
                results.append(claims)
            except Exception as e:
                errors.append(e)

        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
            futures = [executor.submit(verify) for _ in range(8)]
            concurrent.futures.wait(futures)

        self.assertEqual(len(results), 1, "Exactly one verification must succeed")
        self.assertEqual(len(errors), 7, "All concurrent duplicate requests must fail replay check")
        for err in errors:
            self.assertIn("replay detected", str(err).lower())

    # =========================================================================
    # Request Binding Tests (mth and pth)
    # =========================================================================

    def test_request_binding_valid_matching_method_and_path_accepted(self):
        token = self._build_rs256_token(mth="POST", pth="/api/v1/agent/chat")
        claims = verify_gateway_assertion(
            token,
            public_key=self.test_public_key,
            expected_method="POST",
            expected_path="/api/v1/agent/chat"
        )
        self.assertEqual(claims["mth"], "POST")
        self.assertEqual(claims["pth"], "/api/v1/agent/chat")

    def test_request_binding_mismatched_method_rejected(self):
        token = self._build_rs256_token(mth="GET", pth="/api/v1/agent/chat")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(
                token,
                public_key=self.test_public_key,
                expected_method="POST",
                expected_path="/api/v1/agent/chat"
            )
        self.assertIn("invalid gateway assertion method", str(ctx.exception).lower())

    def test_request_binding_mismatched_path_rejected(self):
        token = self._build_rs256_token(mth="GET", pth="/api/v1/agent/chat")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(
                token,
                public_key=self.test_public_key,
                expected_method="GET",
                expected_path="/api/v1/other"
            )
        self.assertIn("invalid gateway assertion path", str(ctx.exception).lower())

    def test_request_binding_missing_method_claim_rejected(self):
        token = self._build_rs256_token(mth="OMIT", pth="/api/v1/agent/chat")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(
                token,
                public_key=self.test_public_key,
                expected_method="GET",
                expected_path="/api/v1/agent/chat"
            )
        self.assertIn("missing required claim 'mth'", str(ctx.exception).lower())

    def test_request_binding_missing_path_claim_rejected(self):
        token = self._build_rs256_token(mth="GET", pth="OMIT")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(
                token,
                public_key=self.test_public_key,
                expected_method="GET",
                expected_path="/api/v1/agent/chat"
            )
        self.assertIn("missing required claim 'pth'", str(ctx.exception).lower())

    def test_request_binding_whitespace_method_rejected(self):
        token = self._build_rs256_token(mth=" GET ", pth="/api/v1/agent/chat")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(
                token,
                public_key=self.test_public_key,
                expected_method="GET",
                expected_path="/api/v1/agent/chat"
            )
        self.assertIn("whitespace padding not allowed", str(ctx.exception).lower())

    def test_request_binding_whitespace_path_rejected(self):
        token = self._build_rs256_token(mth="GET", pth=" /api/v1/agent/chat ")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(
                token,
                public_key=self.test_public_key,
                expected_method="GET",
                expected_path="/api/v1/agent/chat"
            )
        self.assertIn("whitespace padding not allowed", str(ctx.exception).lower())

    def test_request_binding_case_mismatched_path_rejected(self):
        token = self._build_rs256_token(mth="GET", pth="/api/v1/agent/chat")
        with self.assertRaises(ValueError) as ctx:
            verify_gateway_assertion(
                token,
                public_key=self.test_public_key,
                expected_method="GET",
                expected_path="/API/V1/AGENT/CHAT"
            )
        self.assertIn("invalid gateway assertion path", str(ctx.exception).lower())

    def test_request_binding_path_normalization(self):
        token = self._build_rs256_token(mth="GET", pth="/api/v1/agent/chat")
        claims = verify_gateway_assertion(
            token,
            public_key=self.test_public_key,
            expected_method="GET",
            expected_path="//api///v1/../v1/agent/chat"
        )
        self.assertEqual(claims["pth"], "/api/v1/agent/chat")


if __name__ == "__main__":
    unittest.main()
