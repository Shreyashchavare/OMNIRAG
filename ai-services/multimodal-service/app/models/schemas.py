from pydantic import BaseModel, Field


class OCRResponse(BaseModel):
    text: str

class PDFResponse(BaseModel):
    text: str
    pages: int

class ChunkRequest(BaseModel):
    text: str = Field(..., min_length=1)
    chunk_size: int = Field(default=1000, gt=0)
    chunk_overlap: int = Field(default=200, ge=0)

class ChunkResponse(BaseModel):
    chunks: list[str]
    total_chunks: int

class SpeechResponse(BaseModel):
    text: str
    language: str
