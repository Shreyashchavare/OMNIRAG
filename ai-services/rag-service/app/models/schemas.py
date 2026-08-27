from pydantic import BaseModel


class QueryRequest(BaseModel):
    query: str


class QueryEmbeddingResponse(BaseModel):
    query: str
    embedding: list[float]
    dimensions: int


class IndexRequest(BaseModel):
    chunks: list[dict]


class IndexResponse(BaseModel):
    indexed: int


class SearchRequest(BaseModel):
    query: str
    top_k: int = 5


class SearchResult(BaseModel):
    score: float
    document: dict


class SearchResponse(BaseModel):
    results: list[SearchResult] 