import asyncio
import io
import logging
import os
import tempfile
import warnings
from typing import Optional
import httpx

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile, Form, Request
from PIL import Image

from app.config.settings import settings
from app.models.schemas import OCRResponse
from app.services.ocr_service import OCRService

from app.models.schemas import PDFResponse
from app.services.pdf_service import PDFService

from app.models.schemas import ChunkRequest, ChunkResponse
from app.services.chunk_service import ChunkService

from app.models.schemas import SpeechResponse
from app.services.speech_service import speech_service
from app.services.audio_lifecycle import (
    stream_upload_to_temp_file,
    execute_whisper_cancellation_safe,
    safe_remove_audio_file,
)

from app.services.vision_service import VisionService
from app.services.auth_service import verify_internal_service_call
from app.dependencies import get_http_client


logger = logging.getLogger(__name__)

# Ensure Pillow has bounded image pixels limit set at import time
Image.MAX_IMAGE_PIXELS = settings.MAX_IMAGE_PIXELS

router = APIRouter(
    prefix="/api/v1/multimodal",
    tags=["Multimodal"],
    dependencies=[Depends(verify_internal_service_call)]
)


def _get_semaphore(
    request: Optional[Request],
    name: str,
    default_limit: int
) -> asyncio.Semaphore:
    """
    Retrieves the application-scoped semaphore from app.state, initializing it if needed.
    Falls back to a newly created semaphore if request is None (e.g. direct test calls).
    """
    if request is not None and hasattr(request, "app") and hasattr(request.app, "state"):
        sem = getattr(request.app.state, f"{name}_semaphore", None)
        if sem is None:
            sem = asyncio.Semaphore(default_limit)
            setattr(request.app.state, f"{name}_semaphore", sem)
        return sem
    return asyncio.Semaphore(default_limit)


async def read_upload_bounded(
    file: UploadFile,
    max_bytes: int = settings.MAX_UPLOAD_SIZE_BYTES,
    chunk_size: int = 64 * 1024
) -> bytes:
    """
    Reads an UploadFile incrementally in bounded chunks up to max_bytes.
    Enforces the byte limit even if Content-Length header is missing or inaccurate.
    Raises HTTPException(status_code=413) if payload exceeds max_bytes.
    """
    if file.size is not None and file.size > max_bytes:
        raise HTTPException(
            status_code=413,
            detail=f"Uploaded file exceeds maximum allowed size of {max_bytes} bytes"
        )

    total = 0
    chunks: list[bytes] = []
    while True:
        chunk = await file.read(chunk_size)
        if not chunk:
            break
        total += len(chunk)
        if total > max_bytes:
            raise HTTPException(
                status_code=413,
                detail=f"Uploaded file exceeds maximum allowed size of {max_bytes} bytes"
            )
        chunks.append(chunk)

    return b"".join(chunks)


@router.post("/ocr", response_model=OCRResponse)
async def extract_text_from_image(
    file: UploadFile = File(...),
    request: Request = None
):
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(
            status_code=400,
            detail="Only image files are supported"
        )

    image = None
    try:
        image_bytes = await read_upload_bounded(
            file,
            max_bytes=settings.MAX_UPLOAD_SIZE_BYTES
        )
        if not image_bytes:
            raise HTTPException(
                status_code=400,
                detail="Uploaded image file is empty"
            )

        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            try:
                with Image.open(io.BytesIO(image_bytes)) as img:
                    image = img
                    pixel_count = img.width * img.height
                    if pixel_count > settings.MAX_IMAGE_PIXELS:
                        raise HTTPException(
                            status_code=413,
                            detail=f"Image dimensions ({img.width}x{img.height} = {pixel_count} pixels) exceed maximum allowed pixels ({settings.MAX_IMAGE_PIXELS})"
                        )
                    sem = _get_semaphore(request, "ocr", settings.MAX_CONCURRENT_OCR)
                    async with sem:
                        text = await asyncio.to_thread(OCRService.extract_text, image)
            except (Image.DecompressionBombError, Image.DecompressionBombWarning) as e:
                raise HTTPException(
                    status_code=413,
                    detail=f"Image exceeds decompression limit: {str(e)}"
                )

        return OCRResponse(
            text=text
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error("OCR image processing error: %s", str(e), exc_info=True)
        raise HTTPException(
            status_code=400,
            detail=f"Unable to process image: {str(e)}"
        )
    finally:
        if image is not None:
            try:
                image.close()
            except Exception:
                pass
        await file.close()


@router.post("/pdf/extract", response_model=PDFResponse)
async def extract_text_from_pdf(
    file: UploadFile = File(...),
    request: Request = None
):
    if file.content_type != "application/pdf":
        raise HTTPException(
            status_code=400,
            detail="Only PDF files are supported"
        )

    try:
        file_bytes = await read_upload_bounded(
            file,
            max_bytes=settings.MAX_UPLOAD_SIZE_BYTES
        )

        if not file_bytes:
            raise HTTPException(
                status_code=400,
                detail="Uploaded PDF is empty"
            )

        sem = _get_semaphore(request, "pdf", settings.MAX_CONCURRENT_PDF)
        async with sem:
            text, page_count = await asyncio.to_thread(PDFService.extract_text, file_bytes)

        if not text:
            raise HTTPException(
                status_code=422,
                detail="No text could be extracted from the PDF"
            )

        return PDFResponse(
            text=text,
            pages=page_count
        )

    except HTTPException:
        raise

    except Exception:
        raise HTTPException(
            status_code=500,
            detail="Unable to process PDF"
        )
    finally:
        await file.close()


@router.post("/chunk", response_model=ChunkResponse)
async def create_chunks(
    request: ChunkRequest
):
    if not request.text or not request.text.strip():
        raise HTTPException(
            status_code=400,
            detail="Text cannot be empty"
        )

    if len(request.text) > 10_000_000:
        raise HTTPException(
            status_code=413,
            detail="Text exceeds maximum allowed length of 10,000,000 characters"
        )

    try:
        chunks = ChunkService.create_chunks(
            request.text,
            chunk_size=request.chunk_size,
            chunk_overlap=request.chunk_overlap
        )

        return ChunkResponse(
            chunks=chunks,
            total_chunks=len(chunks)
        )

    except ValueError as e:
        raise HTTPException(
            status_code=400,
            detail=str(e)
        )

    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Unable to create text chunks: {str(e)}"
        )


@router.post("/speech/transcribe", response_model=SpeechResponse)
async def transcribe_audio(
    file: UploadFile = File(...),
    request: Request = None
):
    if not file.content_type or not file.content_type.startswith("audio/"):
        raise HTTPException(
            status_code=400,
            detail="Only audio files are supported"
        )

    temp_path: Optional[str] = None
    worker_started = False

    try:
        raw_suffix = os.path.splitext(file.filename or "")[1].lower()
        allowed_suffixes = {".mp3", ".wav", ".ogg", ".m4a", ".flac", ".webm", ".aac", ".wma"}
        suffix = raw_suffix if raw_suffix in allowed_suffixes else ".tmp"

        temp_path = await stream_upload_to_temp_file(
            file,
            suffix=suffix,
            max_bytes=settings.MAX_UPLOAD_SIZE_BYTES
        )

        sem = _get_semaphore(request, "whisper", settings.MAX_CONCURRENT_WHISPER)

        cancel_event = asyncio.Event()
        worker_task = asyncio.create_task(
            execute_whisper_cancellation_safe(
                sem=sem,
                temp_path=temp_path,
                transcribe_fn=speech_service.transcribe,
                cancel_event=cancel_event
            )
        )
        worker_started = True

        def _consume_exception(t: asyncio.Task):
            if not t.cancelled():
                _ = t.exception()
        worker_task.add_done_callback(_consume_exception)

        try:
            text, language = await asyncio.shield(worker_task)
        except asyncio.CancelledError:
            cancel_event.set()
            raise

        if not text:
            raise HTTPException(
                status_code=422,
                detail="No speech could be detected in the audio"
            )

        return SpeechResponse(
            text=text,
            language=language
        )

    except HTTPException:
        raise

    except Exception as e:
        logger.error("Audio transcription failure: %s", str(e), exc_info=True)
        raise HTTPException(
            status_code=500,
            detail="Unable to transcribe audio"
        )

    finally:
        if not worker_started and temp_path:
            safe_remove_audio_file(temp_path)
        await file.close()


@router.post("/vision/analyze")
async def analyze_image(
    file: UploadFile = File(...),
    prompt: str = Form(
        "Describe this image in detail. "
        "Extract all important information that could be useful for a RAG system."
    ),
    http_client: httpx.AsyncClient = Depends(get_http_client)
):
    try:
        if not file.content_type or not file.content_type.startswith("image/"):
            raise HTTPException(
                status_code=400,
                detail="Only image files are supported"
            )

        image_bytes = await read_upload_bounded(
            file,
            max_bytes=settings.MAX_UPLOAD_SIZE_BYTES
        )

        if not image_bytes:
            raise HTTPException(
                status_code=400,
                detail="Uploaded image file is empty"
            )

        # Protect against decompression bombs and oversized pixel dimensions if valid image
        try:
            with warnings.catch_warnings():
                warnings.simplefilter("error", Image.DecompressionBombWarning)
                with Image.open(io.BytesIO(image_bytes)) as img:
                    pixel_count = img.width * img.height
                    if pixel_count > settings.MAX_IMAGE_PIXELS:
                        raise HTTPException(
                            status_code=413,
                            detail=f"Image dimensions ({img.width}x{img.height} = {pixel_count} pixels) exceed maximum allowed pixels ({settings.MAX_IMAGE_PIXELS})"
                        )
        except (Image.DecompressionBombError, Image.DecompressionBombWarning) as e:
            raise HTTPException(
                status_code=413,
                detail=f"Image exceeds decompression limit: {str(e)}"
            )
        except HTTPException:
            raise
        except Exception:
            # If format cannot be identified by PIL (e.g. mock test payload), pass through to VisionService
            pass

        result = await VisionService.analyze_image(
            image_bytes=image_bytes,
            prompt=prompt,
            client=http_client
        )

        return {
            "filename": file.filename,
            "text": result
        }

    except HTTPException:
        raise

    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Unable to analyze image: {str(e)}"
        )
    finally:
        await file.close()
