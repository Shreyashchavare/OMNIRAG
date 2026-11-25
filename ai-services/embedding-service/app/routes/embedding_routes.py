from typing import Optional
import httpx
from fastapi import APIRouter, Depends, HTTPException

from app.schemas.embedding_schema import (
    EmbeddingRequest,
    BatchEmbeddingRequest
)
from app.dependencies import get_http_client
from app.services.embedding_service import EmbeddingService
from app.services.auth_service import verify_internal_service_call


router = APIRouter(
    prefix="/api/v1/embeddings",
    tags=["Embeddings"],
    dependencies=[Depends(verify_internal_service_call)]
)

embedding_service = EmbeddingService()


@router.post("")
async def create_embedding(
    request: EmbeddingRequest,
    http_client: httpx.AsyncClient = Depends(get_http_client)
):

    try:

        embedding = await embedding_service.generate_embedding(
            request.text,
            client=http_client
        )

        return {
            "model": embedding_service.model,
            "embedding": embedding,
            "dimension": len(embedding)
        }

    except Exception as e:

        raise HTTPException(
            status_code=502,
            detail=f"Embedding service error: {str(e)}"
        )


@router.post("/batch")
async def create_batch_embeddings(
    request: BatchEmbeddingRequest,
    http_client: httpx.AsyncClient = Depends(get_http_client)
):

    try:

        embeddings = await embedding_service.generate_embeddings(
            request.texts,
            client=http_client
        )

        return {
            "model": embedding_service.model,
            "embeddings": embeddings,
            "dimension": len(embeddings[0])
        }

    except Exception as e:

        raise HTTPException(
            status_code=502,
            detail=f"Embedding service error: {str(e)}"
        )