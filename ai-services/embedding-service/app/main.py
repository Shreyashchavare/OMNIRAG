from contextlib import asynccontextmanager
import logging
import httpx
from fastapi import FastAPI, Request

from app.config.settings import settings
from app.routes.embedding_routes import router as embedding_router
from app.services.embedding_service import EmbeddingService
from app.config.logging_config import configure_logging

configure_logging()
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Enforce fail-closed configuration validation before initializing resources
    settings.validate_startup_configuration()

    limits = httpx.Limits(
        max_connections=settings.HTTP_MAX_CONNECTIONS,
        max_keepalive_connections=settings.HTTP_MAX_KEEPALIVE_CONNECTIONS,
        keepalive_expiry=settings.HTTP_KEEPALIVE_EXPIRY
    )
    client = None
    try:
        client = httpx.AsyncClient(limits=limits)
        app.state.http_client = client
        logger.info("Application-scoped httpx.AsyncClient initialized for Embedding Service")
    except Exception as e:
        logger.error("Failed to initialize HTTP client for Embedding Service: %s", str(e), exc_info=True)
        if client is not None:
            await client.aclose()
        raise

    try:
        yield
    finally:
        if client is not None:
            try:
                await client.aclose()
                logger.info("Application-scoped httpx.AsyncClient closed gracefully for Embedding Service")
            except Exception as e:
                logger.error("Error closing application-scoped HTTP client for Embedding Service: %s", str(e), exc_info=True)


app = FastAPI(
    title="OMRAGUL Embedding Service",
    version="1.0.0",
    description="Embedding generation service for the OMRAGUL RAG system",
    lifespan=lifespan
)


app.include_router(embedding_router)

embedding_service = EmbeddingService()


@app.get("/health")
async def health_check(request: Request):
    client = getattr(request.app.state, "http_client", None)
    ollama_available = await embedding_service.check_ollama(client=client)

    return {
        "status": "UP" if ollama_available else "DEGRADED",
        "service": "embedding-service",
        "ollama": ollama_available,
        "model": embedding_service.model
    }
