from fastapi import APIRouter, HTTPException

from app.schemas.embedding_schema import (
    EmbeddingRequest,
    BatchEmbeddingRequest
)

from app.services.embedding_service import EmbeddingService


router = APIRouter(
    prefix="/api/v1/embeddings",
    tags=["Embeddings"]
)

embedding_service = EmbeddingService()


@router.post("")
async def create_embedding(request: EmbeddingRequest):

    try:

        embedding = await embedding_service.generate_embedding(
            request.text
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
    request: BatchEmbeddingRequest
):

    try:

        embeddings = await embedding_service.generate_embeddings(
            request.texts
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