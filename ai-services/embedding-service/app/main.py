from fastapi import FastAPI

from app.routes.embedding_routes import router as embedding_router
from app.services.embedding_service import EmbeddingService
from app.config.logging_config import configure_logging

configure_logging()

app = FastAPI(
    title="OMRAGUL Embedding Service",
    version="1.0.0",
    description="Embedding generation service for the OMRAGUL RAG system"
)


app.include_router(embedding_router)

embedding_service = EmbeddingService()


@app.get("/health")
async def health_check():

    ollama_available = await embedding_service.check_ollama()

    return {
        "status": "UP" if ollama_available else "DEGRADED",
        "service": "embedding-service",
        "ollama": ollama_available,
        "model": embedding_service.model
    }