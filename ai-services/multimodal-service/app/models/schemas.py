from typing import Optional
from pydantic import BaseModel, Field, model_validator


class OCRResponse(BaseModel):
    text: str

class PDFResponse(BaseModel):
    text: str
    pages: int

class ChunkRequest(BaseModel):
    text: Optional[str] = None
    content: Optional[str] = None
    chunk_size: int = Field(default=1000, gt=0)
    chunk_overlap: int = Field(default=200, ge=0)

    @model_validator(mode="before")
    @classmethod
    def resolve_text_or_content(cls, data):
        if isinstance(data, dict):
            t = data.get("text") or data.get("content")
            if not t or not str(t).strip():
                raise ValueError("Either 'text' or 'content' must be provided and non-empty")
            data["text"] = str(t)
        return data

class ChunkResponse(BaseModel):
    chunks: list[str]
    total_chunks: int
    totalChunks: Optional[int] = None

    @model_validator(mode="after")
    def populate_camel_case_total_chunks(self):
        if self.totalChunks is None:
            self.totalChunks = self.total_chunks
        return self

class SpeechResponse(BaseModel):
    text: str
    language: str
