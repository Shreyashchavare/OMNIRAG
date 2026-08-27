from fastapi import APIRouter, HTTPException

from app.models.schemas import (
    QueryRequest,
    QueryEmbeddingResponse
)

from app.services.embedding_client import EmbeddingClient
from app.services.vector_store_instance import vector_store

from app.models.schemas import IndexRequest
from app.models.schemas import IndexResponse

from app.models.schemas import SearchRequest
from app.models.schemas import SearchResponse
from app.models.schemas import SearchResult


router = APIRouter()


@router.get("/health")
async def health():
    return {
        "status": "UP",
        "service": "rag-service"
    }


@router.post(
    "/query/embedding",
    response_model=QueryEmbeddingResponse
)
async def generate_query_embedding(
    request: QueryRequest
):
    try:

        if not request.query.strip():
            raise HTTPException(
                status_code=400,
                detail="Query cannot be empty"
            )

        embedding = await EmbeddingClient.generate_embedding(
            request.query
        )

        return QueryEmbeddingResponse(
            query=request.query,
            embedding=embedding,
            dimensions=len(embedding)
        )

    except HTTPException:
        raise

    except Exception:
        raise HTTPException(
            status_code=502,
            detail="Unable to generate query embedding"
        )


@router.post("/index", response_model=IndexResponse)
async def index_chunks(request: IndexRequest):

    try:

        if not request.chunks:
            raise HTTPException(
                status_code=400,
                detail="Chunks cannot be empty"
            )

        embeddings = []
        documents = []

        for chunk in request.chunks:

            text = chunk.get("text")

            if not text:
                continue

            embedding = await EmbeddingClient.generate_embedding(
                text
            )

            embeddings.append(embedding)
            documents.append(chunk)

        if not embeddings:
            raise HTTPException(
                status_code=400,
                detail="No valid chunks found"
            )

        vector_store.add(
            embeddings,
            documents
        )

        return IndexResponse(
            indexed=len(documents)
        )

    except HTTPException:
        raise

    except Exception:
        raise HTTPException(
            status_code=500,
            detail="Unable to index chunks"
        )


@router.post(
    "/search",
    response_model=SearchResponse
)
async def search(request: SearchRequest):

    try:

        if not request.query.strip():
            raise HTTPException(
                status_code=400,
                detail="Query cannot be empty"
            )

        if request.top_k <= 0:
            raise HTTPException(
                status_code=400,
                detail="top_k must be greater than 0"
            )

        query_embedding = (
            await EmbeddingClient.generate_embedding(
                request.query
            )
        )

        results = vector_store.search(
            query_embedding=query_embedding,
            top_k=request.top_k
        )

        return SearchResponse(
            results=results
        )

    except HTTPException:
        raise

    except Exception:
        raise HTTPException(
            status_code=500,
            detail="Unable to search vector store"
        )