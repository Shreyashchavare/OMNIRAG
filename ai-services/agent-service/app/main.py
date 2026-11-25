from contextlib import asynccontextmanager
import logging
import httpx
from fastapi import FastAPI

from app.config.settings import settings
from app.api.routes import router

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
        logger.info("Application-scoped httpx.AsyncClient initialized for %s", settings.APP_NAME)
    except Exception as e:
        logger.error("Failed to initialize HTTP client for %s: %s", settings.APP_NAME, str(e), exc_info=True)
        if client is not None:
            await client.aclose()
        raise

    try:
        yield
    finally:
        if client is not None:
            try:
                await client.aclose()
                logger.info("Application-scoped httpx.AsyncClient closed gracefully for %s", settings.APP_NAME)
            except Exception as e:
                logger.error("Error closing application-scoped HTTP client for %s: %s", settings.APP_NAME, str(e), exc_info=True)


app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    lifespan=lifespan
)


app.include_router(router)


@app.get("/health")
async def health():
    return {
        "status": "UP",
        "service": settings.APP_NAME
    }
