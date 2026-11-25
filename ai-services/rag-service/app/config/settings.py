import logging
from pathlib import Path
from typing import Optional
from pydantic_settings import BaseSettings, SettingsConfigDict

logger = logging.getLogger(__name__)


def _resolve_env_files() -> tuple[str, ...]:
    """
    Locates the authoritative repository root .env file and falls back to
    the current working directory .env.
    Process environment variables always take precedence over .env values.
    """
    candidates: list[str] = []
    current = Path(__file__).resolve().parent
    for parent in [current] + list(current.parents):
        if (parent / "docker-compose.dev.yml").is_file() or (parent / ".git").exists():
            root_env = parent / ".env"
            if root_env.is_file():
                candidates.append(str(root_env))
            break

    cwd_env = Path.cwd() / ".env"
    if cwd_env.is_file() and str(cwd_env) not in candidates:
        candidates.append(str(cwd_env))

    if not candidates:
        candidates.append(".env")

    return tuple(candidates)


class Settings(BaseSettings):

    app_name: str = "OMNIRAG RAG Service"
    app_version: str = "1.0.0"

    embedding_service_url: str = "http://localhost:8001"

    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "qwen3:4b"

    embedding_dimension: int = 1024

    knowledge_service_url: str = "http://localhost:8082"
    internal_service_secret: str = ""
    gateway_assertion_public_key_path: Optional[str] = None
    gateway_assertion_expected_audience: str = "rag-service"

    http_max_connections: int = 100
    http_max_keepalive_connections: int = 20
    http_keepalive_expiry: float = 30.0

    @property
    def HTTP_MAX_CONNECTIONS(self) -> int:
        return self.http_max_connections

    @property
    def HTTP_MAX_KEEPALIVE_CONNECTIONS(self) -> int:
        return self.http_max_keepalive_connections

    @property
    def HTTP_KEEPALIVE_EXPIRY(self) -> float:
        return self.http_keepalive_expiry

    @property
    def INTERNAL_SERVICE_SECRET(self) -> str:
        return self.internal_service_secret

    @INTERNAL_SERVICE_SECRET.setter
    def INTERNAL_SERVICE_SECRET(self, value: str) -> None:
        self.internal_service_secret = value

    @property
    def GATEWAY_ASSERTION_PUBLIC_KEY_PATH(self) -> Optional[str]:
        return self.gateway_assertion_public_key_path

    @GATEWAY_ASSERTION_PUBLIC_KEY_PATH.setter
    def GATEWAY_ASSERTION_PUBLIC_KEY_PATH(self, value: Optional[str]) -> None:
        self.gateway_assertion_public_key_path = value

    @property
    def GATEWAY_ASSERTION_EXPECTED_AUDIENCE(self) -> str:
        return self.gateway_assertion_expected_audience

    @GATEWAY_ASSERTION_EXPECTED_AUDIENCE.setter
    def GATEWAY_ASSERTION_EXPECTED_AUDIENCE(self, value: str) -> None:
        self.gateway_assertion_expected_audience = value

    def validate_startup_configuration(self) -> None:
        """
        Enforces fail-closed configuration validation before application startup.
        1. Rejects missing, empty, or whitespace-only internal service secrets.
        2. Validates Gateway assertion public key existence and parseability.
        Never logs or exposes secret or key values in exception messages.
        """
        secret = (
            getattr(self, "INTERNAL_SERVICE_SECRET", None)
            or getattr(self, "internal_service_secret", None)
        )
        if secret is None or not str(secret).strip():
            logger.error("Startup failed: INTERNAL_SERVICE_SECRET is missing, empty, or whitespace-only")
            raise RuntimeError("INTERNAL_SERVICE_SECRET must be configured and non-empty for service startup")

        exp_aud = (
            getattr(self, "GATEWAY_ASSERTION_EXPECTED_AUDIENCE", None)
            or getattr(self, "gateway_assertion_expected_audience", None)
        )
        if exp_aud is None or not str(exp_aud).strip():
            logger.error("Startup failed: GATEWAY_ASSERTION_EXPECTED_AUDIENCE is missing, empty, or whitespace-only")
            raise RuntimeError("GATEWAY_ASSERTION_EXPECTED_AUDIENCE must be configured and non-empty for service startup")
        if str(exp_aud).strip() != "rag-service":
            logger.error("Startup failed: GATEWAY_ASSERTION_EXPECTED_AUDIENCE must match canonical service identity 'rag-service'")
            raise RuntimeError("GATEWAY_ASSERTION_EXPECTED_AUDIENCE must match canonical service identity 'rag-service'")

        pub_key_path = (
            getattr(self, "GATEWAY_ASSERTION_PUBLIC_KEY_PATH", None)
            or getattr(self, "gateway_assertion_public_key_path", None)
        )
        from app.services.auth_service import resolve_and_validate_public_key
        resolve_and_validate_public_key(pub_key_path)

    model_config = SettingsConfigDict(
        env_file=_resolve_env_files(),
        env_file_encoding="utf-8",
        extra="ignore"
    )


settings = Settings()