import hmac
import logging
from fastapi import HTTPException, Request

from app.config.settings import settings

logger = logging.getLogger(__name__)


async def verify_internal_service_call(request: Request) -> str:
    """
    FastAPI dependency that enforces internal microservice authentication.
    Compares the provided secret in constant-time using hmac.compare_digest.
    Fails closed with HTTP 500 if the server-side secret is missing or unconfigured.
    Fails closed with HTTP 401 if incoming secret header is missing or invalid.
    Returns verified caller identification.
    """
    server_secret = getattr(settings, "INTERNAL_SERVICE_SECRET", None) or getattr(settings, "internal_service_secret", None)
    if not server_secret or not str(server_secret).strip():
        logger.error("Internal service secret is not configured on multimodal-service")
        raise HTTPException(
            status_code=500,
            detail="Internal service secret not configured on target service"
        )

    provided_secret = (
        request.headers.get("X-Internal-Service-Secret")
        or request.headers.get("X-Internal-Secret")
    )

    if not provided_secret or not str(provided_secret).strip():
        logger.warning("Rejected unauthenticated request to %s: missing secret header", request.url.path)
        raise HTTPException(
            status_code=401,
            detail="Authentication required: missing or invalid internal service credentials"
        )

    if not hmac.compare_digest(provided_secret.strip(), str(server_secret).strip()):
        logger.warning("Rejected unauthorized request to %s: invalid secret credentials", request.url.path)
        raise HTTPException(
            status_code=401,
            detail="Authentication required: missing or invalid internal service credentials"
        )

    caller = request.headers.get("X-Internal-Caller-Service") or "internal-service"
    return caller
