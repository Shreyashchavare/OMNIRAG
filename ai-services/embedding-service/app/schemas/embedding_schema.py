from pydantic import BaseModel, Field


class EmbeddingRequest(BaseModel):
    text: str = Field(..., min_length=1)


class BatchEmbeddingRequest(BaseModel):
    texts: list[str] = Field(..., min_length=1)