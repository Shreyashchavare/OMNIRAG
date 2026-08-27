from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):

    app_name: str = "OMNIRAG RAG Service"
    app_version: str = "1.0.0"

    embedding_service_url: str

    ollama_base_url: str 
    ollama_model: str 

    embedding_dimension: int

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8"
    )


settings = Settings()