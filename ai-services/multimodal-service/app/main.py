import asyncio
from contextlib import asynccontextmanager
import logging
import httpx
from fastapi import FastAPI
from PIL import Image

from app.api.routes.multimodal_routes import router as multimodal_router
from app.config.settings import settings
from app.services.audio_lifecycle import cleanup_orphaned_audio_files

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Enforce fail-closed configuration validation before creating resources
    settings.validate_startup_configuration()

    # Set Pillow maximum allowed image pixels to prevent decompression bombs
    Image.MAX_IMAGE_PIXELS = settings.MAX_IMAGE_PIXELS
    logger.info("Configured Pillow Image.MAX_IMAGE_PIXELS = %d", settings.MAX_IMAGE_PIXELS)

    # Initialize bounded concurrency semaphores for CPU/inference operations
    app.state.ocr_semaphore = asyncio.Semaphore(settings.MAX_CONCURRENT_OCR)
    app.state.pdf_semaphore = asyncio.Semaphore(settings.MAX_CONCURRENT_PDF)
    app.state.whisper_semaphore = asyncio.Semaphore(settings.MAX_CONCURRENT_WHISPER)
    logger.info(
        "Initialized concurrency semaphores: OCR=%d, PDF=%d, Whisper=%d",
        settings.MAX_CONCURRENT_OCR,
        settings.MAX_CONCURRENT_PDF,
        settings.MAX_CONCURRENT_WHISPER
    )

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

    # Bounded startup recovery for abandoned audio temporary files
    try:
        cleaned_orphans = cleanup_orphaned_audio_files(
            directory=settings.resolved_audio_temp_dir,
            retention_seconds=settings.AUDIO_TEMP_RETENTION_SECONDS
        )
        logger.info("Startup orphan audio recovery completed (cleaned %d stale files)", cleaned_orphans)
    except Exception as e:
        logger.warning("Error running startup orphan audio recovery: %s", str(e))

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
    description="Multimodal processing service for OMRAGUL",
    lifespan=lifespan
)


app.include_router(multimodal_router)


@app.get("/health")
async def health_check():

    return {
        "status": "UP",
        "service": "multimodal-service"
    }
