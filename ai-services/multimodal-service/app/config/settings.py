from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):

    APP_NAME: str = "OMRAGUL Multimodal Service"
    APP_VERSION: str = "1.0.0"

    HOST: str = "0.0.0.0"
    PORT: int = 8004

    WHISPER_MODEL: str 
    WHISPER_DEVICE: str
    WHISPER_COMPUTE_TYPE: str

    OLLAMA_BASE_URL: str 
    OLLAMA_VISION_MODEL: str 


    model_config = SettingsConfigDict(
            env_file=".env",
            env_file_encoding="utf-8"
        )
    


settings = Settings()