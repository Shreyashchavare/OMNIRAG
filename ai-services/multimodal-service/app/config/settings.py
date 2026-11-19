from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict


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

    WHISPER_MODEL: str = "small"
    WHISPER_DEVICE: str = "cpu"
    WHISPER_COMPUTE_TYPE: str = "int8"

    OLLAMA_BASE_URL: str = "http://localhost:11434"
    OLLAMA_VISION_MODEL: str = "qwen3-vl:4b"

    model_config = SettingsConfigDict(
        env_file=_resolve_env_files(),
        env_file_encoding="utf-8",
        extra="ignore"
    )


settings = Settings()