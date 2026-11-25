import logging
from pathlib import Path
import tempfile
from typing import Optional
from pydantic import field_validator
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

    APP_NAME: str = "OMRAGUL Multimodal Service"
    APP_VERSION: str = "1.0.0"

    HOST: str = "0.0.0.0"
    PORT: int = 8003

    INTERNAL_SERVICE_SECRET: str = ""

    WHISPER_MODEL: str = "small"
    WHISPER_DEVICE: str = "cpu"
    WHISPER_COMPUTE_TYPE: str = "int8"

    OLLAMA_BASE_URL: str = "http://localhost:11434"
    OLLAMA_VISION_MODEL: str = "qwen3-vl:4b"

    HTTP_MAX_CONNECTIONS: int = 100
    HTTP_MAX_KEEPALIVE_CONNECTIONS: int = 20
    HTTP_KEEPALIVE_EXPIRY: float = 30.0

    MAX_UPLOAD_SIZE_BYTES: int = 100 * 1024 * 1024
    MAX_IMAGE_PIXELS: int = 25_000_000
    MAX_CONCURRENT_OCR: int = 4
    MAX_CONCURRENT_PDF: int = 4
    MAX_CONCURRENT_WHISPER: int = 2

    AUDIO_TEMP_DIR: Optional[str] = None
    AUDIO_TEMP_RETENTION_SECONDS: int = 3600

    @field_validator(
        "MAX_UPLOAD_SIZE_BYTES",
        "MAX_IMAGE_PIXELS",
        "MAX_CONCURRENT_OCR",
        "MAX_CONCURRENT_PDF",
        "MAX_CONCURRENT_WHISPER",
        "AUDIO_TEMP_RETENTION_SECONDS"
    )
    @classmethod
    def validate_positive_int(cls, v: int) -> int:
        if v <= 0:
            raise ValueError("Configuration values must be strictly positive")
        return v

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

    @property
    def resolved_audio_temp_dir(self) -> Path:
        if self.AUDIO_TEMP_DIR and str(self.AUDIO_TEMP_DIR).strip():
            p = Path(self.AUDIO_TEMP_DIR).resolve()
        else:
            p = Path(tempfile.gettempdir()) / "omragul_audio_temp"
        p.mkdir(parents=True, exist_ok=True)
        return p

    model_config = SettingsConfigDict(
        env_file=_resolve_env_files(),
        env_file_encoding="utf-8",
        extra="ignore"
    )


settings = Settings()