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

    APP_NAME: str = "OMRAGUL Agent Service"
    APP_VERSION: str = "1.0.0"

    HOST: str = "0.0.0.0"
    PORT: int = 8000

    OLLAMA_BASE_URL: str = "http://localhost:11434"
    OLLAMA_MODEL: str = "qwen3-vl:4b"

    RAG_SERVICE_URL: str = "http://localhost:8002"
    MULTIMODAL_SERVICE_URL: str = "http://localhost:8003"

    INTERNAL_SERVICE_SECRET: str = ""
    GATEWAY_ASSERTION_PUBLIC_KEY_PATH: Optional[str] = None
    GATEWAY_ASSERTION_EXPECTED_AUDIENCE: str = "agent-service"

    HTTP_MAX_CONNECTIONS: int = 100
    HTTP_MAX_KEEPALIVE_CONNECTIONS: int = 20
    HTTP_KEEPALIVE_EXPIRY: float = 30.0

    @property
    def internal_service_secret(self) -> str:
        return self.INTERNAL_SERVICE_SECRET

    @internal_service_secret.setter
    def internal_service_secret(self, value: str) -> None:
        self.INTERNAL_SERVICE_SECRET = value

    @property
    def gateway_assertion_public_key_path(self) -> Optional[str]:
        return self.GATEWAY_ASSERTION_PUBLIC_KEY_PATH

    @gateway_assertion_public_key_path.setter
    def gateway_assertion_public_key_path(self, value: Optional[str]) -> None:
        self.GATEWAY_ASSERTION_PUBLIC_KEY_PATH = value

    @property
    def gateway_assertion_expected_audience(self) -> str:
        return self.GATEWAY_ASSERTION_EXPECTED_AUDIENCE

    @gateway_assertion_expected_audience.setter
    def gateway_assertion_expected_audience(self, value: str) -> None:
        self.GATEWAY_ASSERTION_EXPECTED_AUDIENCE = value

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
        if str(exp_aud).strip() != "agent-service":
            logger.error("Startup failed: GATEWAY_ASSERTION_EXPECTED_AUDIENCE must match canonical service identity 'agent-service'")
            raise RuntimeError("GATEWAY_ASSERTION_EXPECTED_AUDIENCE must match canonical service identity 'agent-service'")

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