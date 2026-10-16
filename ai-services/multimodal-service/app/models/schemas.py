from pydantic import BaseModel


class OCRResponse(BaseModel):
    text: str

class PDFResponse(BaseModel):
    text: str
    pages: int

class ChunkResponse(BaseModel):
    chunks: list[str]
    total_chunks: int

class SpeechResponse(BaseModel):
    text: str
    language: str