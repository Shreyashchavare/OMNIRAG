from pydantic_settings import BaseSettings


class Settings(BaseSettings):

    APP_NAME: str = "OMRAGUL Multimodal Service"
    APP_VERSION: str = "1.0.0"

    HOST: str = "0.0.0.0"
    PORT: int = 8004

    WHISPER_MODEL: str = "small"
    WHISPER_DEVICE: str = "cpu"
    WHISPER_COMPUTE_TYPE: str = "int8"

    OLLAMA_BASE_URL: str = "http://localhost:11434"
    OLLAMA_VISION_MODEL: str = "qwen3-vl:4b"


    class Config:
        env_file = ".env"


settings = Settings()