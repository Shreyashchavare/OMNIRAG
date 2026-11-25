import base64
import hashlib
import hmac
import json
import logging
import os
import sys
import threading
import time
import uuid
from pathlib import Path
from typing import Any, Dict, List, Optional

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from fastapi import HTTPException, Request
import jwt

from app.config.settings import settings

logger = logging.getLogger(__name__)

DEFAULT_PUBLIC_KEY_PATH: str = "certs/gateway_assertion_public_key.pem"

_cached_public_key: Optional[rsa.RSAPublicKey] = None


class MissingSecretException(HTTPException, ValueError):
    """Raised when the internal service secret is missing or unconfigured."""
    def __init__(self, detail: str = "Internal service secret not configured for assertion verification"):
        HTTPException.__init__(self, status_code=500, detail=detail)
        ValueError.__init__(self, detail)


class MissingPublicKeyException(HTTPException, ValueError):
    """Raised when the gateway assertion public key is missing or unconfigured."""
    def __init__(self, detail: str = "Gateway assertion public key not configured for RS256 verification"):
        HTTPException.__init__(self, status_code=500, detail=detail)
        ValueError.__init__(self, detail)


class VerifiedIdentity(str):
    """
    Subclasses str so that identity can be used as string (user_id),
    while exposing assertion_token, request_id, and roles.
    """
    user_id: str
    assertion_token: str
    request_id: str
    roles: str

    def __new__(cls, user_id: str, assertion_token: str, request_id: str, roles: str = ""):
        instance = super().__new__(cls, user_id)
        instance.user_id = user_id
        instance.assertion_token = assertion_token
        instance.request_id = request_id
        instance.roles = roles
        return instance


def load_public_key_from_pem(pem_content: str) -> rsa.RSAPublicKey:
    """Parses and validates an RSA public key in PEM format."""
    if not pem_content or not str(pem_content).strip():
        raise ValueError("Public key PEM content is empty")
    clean_pem = pem_content.strip().encode("utf-8")
    try:
        public_key = serialization.load_pem_public_key(clean_pem)
        if not isinstance(public_key, rsa.RSAPublicKey):
            raise ValueError("Public key is not an RSA public key")
        return public_key
    except Exception as e:
        raise ValueError(f"Failed to parse RSA public key PEM: {str(e)}") from e


def resolve_and_load_public_key(configured_path: Optional[str] = None) -> Optional[rsa.RSAPublicKey]:
    """
    Resolves and loads the RSA public key from the explicitly configured path.
    Fails closed (raises FileNotFoundError or ValueError) if configured path is invalid or unreadable.
    Returns None if no key path is configured (failing closed when RS256 tokens are verified).
    Does NOT perform uncontrolled ancestor-directory traversal.
    """
    target_path = (
        configured_path
        if (configured_path is not None and str(configured_path).strip())
        else getattr(settings, "GATEWAY_ASSERTION_PUBLIC_KEY_PATH", None)
        or getattr(settings, "gateway_assertion_public_key_path", None)
    )
    if not target_path or not str(target_path).strip():
        logger.warning("Gateway assertion public key not configured; RS256 assertions will fail closed")
        return None

    p = Path(str(target_path).strip())
    if not p.is_absolute():
        base_dir = os.environ.get("OMRAGUL_BASE_DIR")
        if base_dir and base_dir.strip():
            p = (Path(base_dir.strip()) / p).resolve()
        else:
            p = (Path.cwd() / p).resolve()

    if not p.is_file():
        raise FileNotFoundError(f"Configured Gateway assertion public key file not found: {p}")

    try:
        content = p.read_text(encoding="utf-8")
        return load_public_key_from_pem(content)
    except Exception as e:
        raise ValueError(f"Failed to load or parse Gateway assertion public key from {p}: {str(e)}") from e


resolve_and_validate_public_key = resolve_and_load_public_key


def get_cached_public_key() -> Optional[rsa.RSAPublicKey]:
    """Retrieves cached RSA public key, resolving from filesystem if uninitialized."""
    global _cached_public_key
    if _cached_public_key is None:
        _cached_public_key = resolve_and_load_public_key()
    return _cached_public_key


def set_cached_public_key(key: Optional[rsa.RSAPublicKey]) -> None:
    """Sets or overrides cached RSA public key (useful for tests)."""
    global _cached_public_key
    _cached_public_key = key


class GatewayAssertionReplayService:
    """Interface for atomic Gateway assertion replay detection."""
    def claim_jti(self, jti: str, consumer_service: str, issuer: str, exp: int) -> bool:
        """
        Attempts to claim a jti atomically.
        Returns True if fresh (not replayed).
        Returns False if duplicate (replayed).
        Raises an exception if Redis is unreachable or times out (fail closed).
        """
        raise NotImplementedError


class RedisGatewayAssertionReplayService(GatewayAssertionReplayService):
    """
    Redis-backed distributed replay protection using atomic SET NX EX.
    Key namespace: omragul:replay:{consumer_service}:{issuer}:{jti}
    Value: '1'
    TTL: positive bounded remaining token lifetime: (exp + 10s leeway) - now + 5s buffer (bounded 1-300s).
    Fail-closed: All Redis communication errors fail closed by raising RuntimeError.
    """
    def __init__(
        self,
        redis_client: Optional[Any] = None,
        redis_host: Optional[str] = None,
        redis_port: Optional[int] = None,
        redis_password: Optional[str] = None,
        socket_timeout: float = 2.0,
    ):
        self._client = redis_client
        self._host = redis_host or os.environ.get("REDIS_HOST", "localhost")
        self._port = redis_port or int(os.environ.get("REDIS_PORT", "6379"))
        self._password = redis_password or os.environ.get("REDIS_PASSWORD") or None
        self._socket_timeout = socket_timeout

    def _get_client(self):
        if self._client is None:
            try:
                import redis
                self._client = redis.Redis(
                    host=self._host,
                    port=self._port,
                    password=self._password,
                    socket_timeout=self._socket_timeout,
                    socket_connect_timeout=self._socket_timeout,
                    decode_responses=True,
                )
            except Exception as e:
                logger.error("Failed to initialize Redis client: %s", str(e), exc_info=True)
                raise RuntimeError(f"Replay protection Redis client unavailable: {str(e)}") from e
        return self._client

    def claim_jti(self, jti: str, consumer_service: str, issuer: str, exp: int) -> bool:
        if not jti or not str(jti).strip():
            raise ValueError("jti cannot be blank")
        if not consumer_service or not str(consumer_service).strip():
            raise ValueError("consumer_service cannot be blank")
        if not issuer or not str(issuer).strip():
            raise ValueError("issuer cannot be blank")

        now = int(time.time())
        leeway = 10
        buffer_sec = 5
        ttl = (exp + leeway) - now + buffer_sec
        ttl = max(1, min(ttl, 300))

        key = f"omragul:replay:{consumer_service.strip()}:{issuer.strip()}:{jti.strip()}"
        try:
            client = self._get_client()
            acquired = client.set(key, "1", nx=True, ex=ttl)
            return bool(acquired)
        except Exception as e:
            logger.error("Redis replay check failed fail-closed for key %s: %s", key, str(e), exc_info=True)
            raise RuntimeError(f"Replay protection storage unavailable (fail-closed): {str(e)}") from e


class InMemoryGatewayAssertionReplayService(GatewayAssertionReplayService):
    """
    Thread-safe in-memory replay protection simulating Redis SET NX EX.
    Used for unit testing and local environments when external Redis is unavailable.
    """
    def __init__(self):
        self._lock = threading.Lock()
        self._entries: Dict[str, float] = {}

    def claim_jti(self, jti: str, consumer_service: str, issuer: str, exp: int) -> bool:
        if not jti or not str(jti).strip():
            raise ValueError("jti cannot be blank")
        if not consumer_service or not str(consumer_service).strip():
            raise ValueError("consumer_service cannot be blank")
        if not issuer or not str(issuer).strip():
            raise ValueError("issuer cannot be blank")

        now = time.time()
        leeway = 10
        buffer_sec = 5
        ttl = (exp + leeway) - int(now) + buffer_sec
        ttl = max(1, min(ttl, 300))

        key = f"omragul:replay:{consumer_service.strip()}:{issuer.strip()}:{jti.strip()}"
        with self._lock:
            expired = [k for k, v in self._entries.items() if v <= now]
            for k in expired:
                del self._entries[k]

            if key in self._entries:
                return False
            self._entries[key] = now + ttl
            return True

    def clear(self):
        with self._lock:
            self._entries.clear()


_default_replay_service: Optional[GatewayAssertionReplayService] = None


def get_default_replay_service() -> GatewayAssertionReplayService:
    global _default_replay_service
    if _default_replay_service is None:
        if os.environ.get("OMRAGUL_TEST_MODE", "").lower() in ("true", "1") or "pytest" in sys.modules:
            _default_replay_service = InMemoryGatewayAssertionReplayService()
        else:
            _default_replay_service = RedisGatewayAssertionReplayService()
    return _default_replay_service


def set_default_replay_service(service: Optional[GatewayAssertionReplayService]) -> None:
    global _default_replay_service
    _default_replay_service = service


VALID_HTTP_METHODS = {"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"}


def _normalize_path(path: str) -> str:
    if not path or not str(path).strip():
        return "/"
    p = str(path).strip()
    has_trailing_slash = p.endswith("/") and len(p) > 1
    parts = [segment for segment in p.split("/") if segment and segment != "."]
    resolved: List[str] = []
    for part in parts:
        if part == "..":
            if resolved:
                resolved.pop()
        else:
            resolved.append(part)
    res = "/" + "/".join(resolved)
    if has_trailing_slash and not res.endswith("/"):
        res += "/"
    return res


def _verify_rs256_assertion(
    assertion: str,
    parts: List[str],
    public_key: Optional[rsa.RSAPublicKey],
    expected_audience: Optional[str] = None,
    replay_service: Optional[GatewayAssertionReplayService] = None,
    expected_method: Optional[str] = None,
    expected_path: Optional[str] = None,
) -> Dict[str, Any]:
    """
    Verifies a standard RS256 JWT Gateway Assertion (<header_b64>.<payload_b64>.<sig_b64>).
    Strictly enforces:
    - Header alg is RS256
    - Header typ is JWT
    - Public key is present and valid
    - Signature integrity via PyJWT
    - Payload contains required claims: sub (valid UUID), iss (gateway-service), iat, exp, rid, aud
    - Integer epoch timestamps for iat and exp (rejecting booleans and non-ints)
    - 10-second allowable clock skew for exp and future iat
    - Non-empty request ID (rid)
    - Single textual string aud matching expected_audience
    NEVER falls back to HMAC.
    """
    rem_h = len(parts[0]) % 4
    padded_h = parts[0] + ("=" * (4 - rem_h) if rem_h > 0 else "")
    try:
        header_bytes = base64.urlsafe_b64decode(padded_h.encode("utf-8"))
        raw_header = json.loads(header_bytes.decode("utf-8"))
    except Exception as e:
        raise ValueError(f"Malformed gateway assertion header: {str(e)}") from e

    if not isinstance(raw_header, dict):
        raise ValueError("Malformed gateway assertion header: expected JSON object")

    alg = raw_header.get("alg")
    if alg != "RS256":
        raise ValueError(f"Invalid assertion algorithm: expected RS256, got {alg}")

    typ = raw_header.get("typ")
    if typ != "JWT":
        raise ValueError(f"Invalid assertion type: expected JWT, got {typ}")

    if public_key is None:
        raise MissingPublicKeyException("Gateway assertion public key not configured for RS256 verification")

    if expected_audience is not None:
        if not str(expected_audience).strip():
            raise ValueError("Gateway assertion expected audience not configured")
        exp_aud = str(expected_audience).strip()
    else:
        cfg_aud = getattr(settings, "GATEWAY_ASSERTION_EXPECTED_AUDIENCE", None)
        if cfg_aud is not None and not str(cfg_aud).strip():
            raise ValueError("Gateway assertion expected audience not configured")
        exp_aud = str(cfg_aud).strip() if (cfg_aud and str(cfg_aud).strip()) else "agent-service"

    rem_p = len(parts[1]) % 4
    padded_p = parts[1] + ("=" * (4 - rem_p) if rem_p > 0 else "")
    try:
        payload_bytes = base64.urlsafe_b64decode(padded_p.encode("utf-8"))
        raw_payload = json.loads(payload_bytes.decode("utf-8"))
    except Exception as e:
        raise ValueError(f"Malformed gateway assertion payload: {str(e)}") from e

    if not isinstance(raw_payload, dict):
        raise ValueError("Malformed gateway assertion payload: expected JSON object")

    for req_claim in ("sub", "iss", "iat", "exp", "rid", "aud", "mth", "pth"):
        if req_claim not in raw_payload or raw_payload[req_claim] is None:
            raise ValueError(f"Missing required claim '{req_claim}' in gateway assertion")

    raw_aud = raw_payload.get("aud")
    if not isinstance(raw_aud, str):
        if isinstance(raw_aud, list):
            raise ValueError("Invalid assertion audience: multi-audience array not allowed (expected single string)")
        elif isinstance(raw_aud, dict):
            raise ValueError("Invalid assertion audience: expected string, got object")
        else:
            raise ValueError(f"Invalid assertion audience: expected single string, got {type(raw_aud).__name__}")

    if not raw_aud.strip():
        raise ValueError("Missing or empty assertion audience (aud)")

    if raw_aud != exp_aud:
        raise ValueError(f"Invalid gateway assertion audience: expected {exp_aud}, got {raw_aud}")

    # Validate mth (HTTP method binding)
    raw_mth = raw_payload.get("mth")
    if not isinstance(raw_mth, str):
        raise ValueError(f"Invalid assertion method: expected string, got {type(raw_mth).__name__}")
    if not raw_mth.strip():
        raise ValueError("Missing or empty assertion method (mth)")
    if raw_mth.strip() != raw_mth or any(c in raw_mth for c in " \t\r\n"):
        raise ValueError("Invalid gateway assertion method: whitespace padding not allowed")
    if raw_mth not in VALID_HTTP_METHODS:
        raise ValueError(f"Invalid gateway assertion method verb: {raw_mth}")

    # Validate pth (canonical downstream path binding)
    raw_pth = raw_payload.get("pth")
    if not isinstance(raw_pth, str):
        raise ValueError(f"Invalid assertion path: expected string, got {type(raw_pth).__name__}")
    if not raw_pth.strip():
        raise ValueError("Missing or empty assertion path (pth)")
    if raw_pth.strip() != raw_pth or any(c in raw_pth for c in " \t\r\n"):
        raise ValueError("Invalid gateway assertion path: whitespace padding not allowed")
    if not raw_pth.startswith("/"):
        raise ValueError("Invalid gateway assertion path: must start with '/'")

    iat_raw = raw_payload.get("iat")
    exp_raw = raw_payload.get("exp")
    if type(iat_raw) is not int or type(exp_raw) is not int or isinstance(iat_raw, bool) or isinstance(exp_raw, bool):
        raise ValueError("Invalid gateway assertion timestamps: iat and exp must be integer epoch timestamps")

    try:
        payload = jwt.decode(
            assertion,
            key=public_key,
            algorithms=["RS256"],
            issuer="gateway-service",
            audience=exp_aud,
            leeway=10,
            options={
                "require": ["sub", "iss", "iat", "exp", "rid", "aud", "mth", "pth"],
                "verify_signature": True,
                "verify_exp": True,
                "verify_iat": True,
                "verify_iss": True,
                "verify_aud": True,
            }
        )
    except jwt.ExpiredSignatureError as e:
        raise ValueError(f"Gateway assertion has expired: {str(e)}") from e
    except jwt.ImmatureSignatureError as e:
        raise ValueError(f"Gateway assertion issued-at timestamp is in the future: {str(e)}") from e
    except jwt.InvalidAlgorithmError as e:
        raise ValueError(f"Invalid gateway assertion algorithm: {str(e)}") from e
    except jwt.InvalidSignatureError as e:
        raise ValueError(f"Invalid gateway assertion signature: {str(e)}") from e
    except jwt.InvalidIssuerError as e:
        raise ValueError(f"Invalid gateway assertion issuer: {str(e)}") from e
    except jwt.InvalidAudienceError as e:
        raise ValueError(f"Invalid gateway assertion audience: {str(e)}") from e
    except jwt.MissingRequiredClaimError as e:
        raise ValueError(f"Missing required claim in gateway assertion: {str(e)}") from e
    except jwt.PyJWTError as e:
        raise ValueError(f"Gateway assertion verification failed: {str(e)}") from e

    # Verify signed claims match inspected payload
    if payload.get("mth") != raw_mth:
        raise ValueError("Gateway assertion mth claim mismatch or missing in signed claims")
    if payload.get("pth") != raw_pth:
        raise ValueError("Gateway assertion pth claim mismatch or missing in signed claims")

    # Request binding validation (must precede replay claiming)
    if expected_method is not None:
        if not str(expected_method).strip():
            raise ValueError("Expected method cannot be blank")
        norm_exp_method = str(expected_method).strip().upper()
        if raw_mth != norm_exp_method:
            raise ValueError(f"Invalid gateway assertion method: expected {norm_exp_method}, got {raw_mth}")

    if expected_path is not None:
        if not str(expected_path).strip():
            raise ValueError("Expected path cannot be blank")
        norm_exp_path = _normalize_path(str(expected_path))
        if raw_pth != norm_exp_path:
            raise ValueError(f"Invalid gateway assertion path: expected {norm_exp_path}, got {raw_pth}")

    sub_str = str(payload.get("sub", "")).strip()
    if not sub_str:
        raise ValueError("Missing subject (user identity) in gateway assertion")
    try:
        parsed_uuid = uuid.UUID(sub_str)
        payload["sub"] = str(parsed_uuid)
    except (ValueError, TypeError, AttributeError) as e:
        raise ValueError(f"Invalid UUID subject in gateway assertion: {sub_str}") from e

    now = int(time.time())
    if iat_raw > now + 10:
        raise ValueError("Gateway assertion issued-at timestamp is in the future")
    if exp_raw < now - 10:
        raise ValueError("Gateway assertion has expired")

    rid = str(payload.get("rid", "")).strip()
    if not rid:
        raise ValueError("Missing or empty request ID (rid) in gateway assertion")

    # 6. Assertion identifier (jti) presence and format validation
    if "jti" not in raw_payload or raw_payload.get("jti") is None:
        raise ValueError("Missing required claim 'jti' in gateway assertion")

    raw_jti = raw_payload.get("jti")
    if not isinstance(raw_jti, str):
        raise ValueError(f"Invalid assertion jti: expected string, got {type(raw_jti).__name__}")

    if not raw_jti.strip():
        raise ValueError("Missing or empty assertion identifier (jti)")

    if raw_jti.strip() != raw_jti or " " in raw_jti:
        raise ValueError("Invalid gateway assertion jti: whitespace padding not allowed")

    if len(raw_jti) < 16 or len(raw_jti) > 128:
        raise ValueError(f"Invalid gateway assertion jti length: {len(raw_jti)}")

    claims_jti = payload.get("jti")
    if claims_jti is None or claims_jti != raw_jti:
        raise ValueError("Gateway assertion jti claim mismatch or missing in signed claims")

    # 7. Distributed replay check via Redis / ReplayService
    rs = replay_service if replay_service is not None else get_default_replay_service()
    if rs is not None:
        claimed = rs.claim_jti(raw_jti, exp_aud, "gateway-service", exp_raw)
        if not claimed:
            raise ValueError(f"Gateway assertion replay detected: token with jti '{raw_jti}' has already been processed")

    return payload


def verify_gateway_assertion(
    assertion: str,
    secret: Optional[str] = None,
    public_key: Optional[rsa.RSAPublicKey] = None,
    legacy_hmac_enabled: Optional[bool] = None,
    expected_audience: Optional[str] = None,
    replay_service: Optional[GatewayAssertionReplayService] = None,
    expected_method: Optional[str] = None,
    expected_path: Optional[str] = None,
) -> Dict[str, Any]:
    """
    Verifies Gateway Assertion tokens.
    Accepts exclusively standard RS256 JWT tokens; legacy HMAC tokens are strictly rejected.
    """
    if not assertion or not str(assertion).strip():
        raise ValueError("Missing gateway assertion token")

    parts = str(assertion).strip().split(".")
    if len(parts) == 3:
        pk = public_key if public_key is not None else get_cached_public_key()
        return _verify_rs256_assertion(
            str(assertion).strip(),
            parts,
            pk,
            expected_audience=expected_audience,
            replay_service=replay_service,
            expected_method=expected_method,
            expected_path=expected_path,
        )
    elif len(parts) == 2:
        raise ValueError("Legacy HMAC gateway assertions are rejected; RS256 assertions required")
    else:
        raise ValueError("Malformed gateway assertion structure (expected 3 parts for RS256)")


def extract_and_verify_user_identity(
    request: Request,
    internal_secret: Optional[str] = None,
    public_key: Optional[rsa.RSAPublicKey] = None,
    expected_audience: Optional[str] = None,
    replay_service: Optional[GatewayAssertionReplayService] = None,
    expected_method: Optional[str] = None,
    expected_path: Optional[str] = None,
) -> VerifiedIdentity:
    """
    Extracts and verifies authentic user identity from request headers.
    Enforces that user-scoped Agent chat requires a cryptographically verified Gateway Assertion.
    Does NOT allow internal service secret fallback to establish end-user identity.
    Validates anti-spoofing if X-User-Id is provided.
    Returns VerifiedIdentity containing verified user_id, assertion_token, request_id, and roles.
    """
    assertion_header = request.headers.get("X-Gateway-Assertion")
    claimed_user_id = request.headers.get("X-User-Id")

    if not assertion_header or not assertion_header.strip():
        logger.warning("Rejected user-scoped request: missing X-Gateway-Assertion header")
        raise HTTPException(
            status_code=401,
            detail="Authentication required: missing or invalid gateway assertion"
        )

    req_m = getattr(request, "method", None)
    req_p = getattr(getattr(request, "url", None), "path", None)
    method_to_check = expected_method if expected_method is not None else (req_m if isinstance(req_m, str) else None)
    path_to_check = expected_path if expected_path is not None else (req_p if isinstance(req_p, str) else None)

    try:
        payload = verify_gateway_assertion(
            assertion_header.strip(),
            public_key=public_key,
            expected_audience=expected_audience,
            replay_service=replay_service,
            expected_method=method_to_check,
            expected_path=path_to_check,
        )
    except (MissingSecretException, MissingPublicKeyException) as e:
        raise HTTPException(status_code=500, detail=str(e))
    except RuntimeError as e:
        logger.error("Gateway assertion replay service error (fail-closed): %s", str(e), exc_info=True)
        raise HTTPException(status_code=401, detail=f"Gateway assertion verification unavailable: {str(e)}")
    except ValueError as e:
        logger.warning("Invalid gateway assertion rejected: %s", str(e))
        raise HTTPException(status_code=401, detail=f"Invalid gateway assertion: {str(e)}")

    verified_user_id = str(payload["sub"]).strip()
    request_id = str(payload.get("rid", "")).strip()
    roles = str(payload.get("roles", "")).strip()

    if claimed_user_id and claimed_user_id.strip():
        try:
            claimed_uuid = uuid.UUID(claimed_user_id.strip())
            if str(claimed_uuid).lower() != verified_user_id.lower():
                logger.warning("User ID header spoofing attempt detected: claimed %s != assertion %s",
                               claimed_user_id, verified_user_id)
                raise HTTPException(status_code=401, detail="User identity spoofing detected")
        except (ValueError, TypeError, AttributeError):
            logger.warning("Malformed claimed X-User-Id: %s", claimed_user_id)
            raise HTTPException(status_code=401, detail="User identity spoofing detected")

    return VerifiedIdentity(
        user_id=verified_user_id,
        assertion_token=assertion_header.strip(),
        request_id=request_id,
        roles=roles
    )
