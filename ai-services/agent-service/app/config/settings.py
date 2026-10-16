from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):

    APP_NAME: str = "OMRAGUL Agent Service"
    APP_VERSION: str = "1.0.0"

    HOST: str = "0.0.0.0"
    PORT: int = 8000

    OLLAMA_BASE_URL: str
    OLLAMA_MODEL: str

    RAG_SERVICE_URL: str
    MULTIMODAL_SERVICE_URL: str

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8"
    )


settings = Settings()