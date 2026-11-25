import logging
from pathlib import Path
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

    OLLAMA_BASE_URL: str = "http://localhost:11434"
    EMBEDDING_MODEL: str = "bge-m3"
    INTERNAL_SERVICE_SECRET: str = ""

    HTTP_MAX_CONNECTIONS: int = 100
    HTTP_MAX_KEEPALIVE_CONNECTIONS: int = 20
    HTTP_KEEPALIVE_EXPIRY: float = 30.0

    OLLAMA_TIMEOUT: float = 55.0
    OLLAMA_HEALTH_TIMEOUT: float = 5.0

    @property
    def internal_service_secret(self) -> str:
        return self.INTERNAL_SERVICE_SECRET

    @internal_service_secret.setter
    def internal_service_secret(self, value: str) -> None:
        self.INTERNAL_SERVICE_SECRET = value

    def validate_startup_configuration(self) -> None:
        """
        Enforces fail-closed configuration validation before application startup.
        Rejects missing, empty, or whitespace-only internal service secrets.
        Never logs or exposes the secret value in exception messages.
        """
        secret = (
            getattr(self, "INTERNAL_SERVICE_SECRET", None)
            or getattr(self, "internal_service_secret", None)
        )
        if secret is None or not str(secret).strip():
            logger.error("Startup failed: INTERNAL_SERVICE_SECRET is missing, empty, or whitespace-only")
            raise RuntimeError("INTERNAL_SERVICE_SECRET must be configured and non-empty for service startup")

    model_config = SettingsConfigDict(
        env_file=_resolve_env_files(),
        env_file_encoding="utf-8",
        extra="ignore"
    )


settings = Settings()