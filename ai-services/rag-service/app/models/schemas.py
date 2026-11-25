from typing import Optional
from pydantic import BaseModel, Field


class QueryRequest(BaseModel):
    query: str


class QueryEmbeddingResponse(BaseModel):
    query: str
    embedding: list[float]
    dimensions: int


class IndexRequest(BaseModel):
    chunks: list[dict]
    knowledge_base_id: str = Field(..., min_length=1)


class IndexResponse(BaseModel):
    indexed: int


class SearchRequest(BaseModel):
    query: str = Field(..., min_length=1)
    knowledge_base_id: str = Field(..., min_length=1)
    top_k: int = Field(default=5, gt=0)


class SearchResult(BaseModel):
    score: float
    document: dict


class SearchResponse(BaseModel):
    results: list[SearchResult]


class DeleteDocumentResponse(BaseModel):
    document_id: str
    knowledge_base_id: Optional[str] = None
    deleted_chunks: int


class DeleteKnowledgeBaseResponse(BaseModel):
    knowledge_base_id: str
    deleted: bool
