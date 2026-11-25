import asyncio
import os
import logging
from typing import List, Optional
from fastapi import FastAPI, File, UploadFile, Header, HTTPException, status
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("multimodal-mock")

app = FastAPI(title="Multimodal Service Mock", version="1.0.0")

EXPECTED_SECRET = os.environ.get("INTERNAL_SERVICE_SECRET", "omragul-integration-test-secret-4096-phase2")


def verify_secret(secret_header: Optional[str] = Header(None, alias="X-Internal-Service-Secret"),
                  fallback_header: Optional[str] = Header(None, alias="X-Internal-Secret")):
    provided = secret_header or fallback_header
    if not provided or provided != EXPECTED_SECRET:
        logger.warning("Unauthorized mock call: secret mismatch or missing")
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid internal service secret")


async def check_fault_injection(fault_header: Optional[str]):
    if fault_header == "500":
        logger.info("Injecting fault: HTTP 500")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Mock injected server error")
    elif fault_header == "timeout":
        logger.info("Injecting fault: Timeout (sleeping 35s)")
        await asyncio.sleep(35)


class ChunkRequest(BaseModel):
    content: Optional[str] = None
    text: Optional[str] = None
    chunk_size: Optional[int] = 1000
    chunk_overlap: Optional[int] = 200


class ChunkResponse(BaseModel):
    chunks: List[str]


class PdfResponse(BaseModel):
    text: str
    pages: int = 1


class OcrResponse(BaseModel):
    text: str
    confidence: float = 0.99


class SpeechResponse(BaseModel):
    text: str
    language: str = "en"


@app.get("/health")
def health():
    return {"status": "UP", "service": "multimodal-mock"}


@app.post("/api/v1/multimodal/pdf/extract", response_model=PdfResponse)
async def extract_pdf(
    file: UploadFile = File(...),
    x_internal_service_secret: Optional[str] = Header(None, alias="X-Internal-Service-Secret"),
    x_internal_secret: Optional[str] = Header(None, alias="X-Internal-Secret"),
    x_mock_fault: Optional[str] = Header(None, alias="X-Mock-Fault")
):
    verify_secret(x_internal_service_secret, x_internal_secret)
    await check_fault_injection(x_mock_fault)

    content = await file.read()
    logger.info("Mock PDF extract received file '%s' (%d bytes)", file.filename, len(content))
    return PdfResponse(
        text=f"Deterministic extracted PDF text from {file.filename}. Content size: {len(content)} bytes.",
        pages=1
    )


@app.post("/api/v1/multimodal/ocr", response_model=OcrResponse)
async def extract_ocr(
    file: UploadFile = File(...),
    x_internal_service_secret: Optional[str] = Header(None, alias="X-Internal-Service-Secret"),
    x_internal_secret: Optional[str] = Header(None, alias="X-Internal-Secret"),
    x_mock_fault: Optional[str] = Header(None, alias="X-Mock-Fault")
):
    verify_secret(x_internal_service_secret, x_internal_secret)
    await check_fault_injection(x_mock_fault)

    content = await file.read()
    logger.info("Mock OCR received file '%s' (%d bytes)", file.filename, len(content))
    return OcrResponse(
        text=f"Deterministic OCR text from {file.filename}. Content size: {len(content)} bytes.",
        confidence=0.99
    )


@app.post("/api/v1/multimodal/speech/transcribe", response_model=SpeechResponse)
async def transcribe_speech(
    file: UploadFile = File(...),
    x_internal_service_secret: Optional[str] = Header(None, alias="X-Internal-Service-Secret"),
    x_internal_secret: Optional[str] = Header(None, alias="X-Internal-Secret"),
    x_mock_fault: Optional[str] = Header(None, alias="X-Mock-Fault")
):
    verify_secret(x_internal_service_secret, x_internal_secret)
    await check_fault_injection(x_mock_fault)

    content = await file.read()
    logger.info("Mock speech transcribe received file '%s' (%d bytes)", file.filename, len(content))
    return SpeechResponse(
        text=f"Deterministic transcribed audio text from {file.filename}. Content size: {len(content)} bytes.",
        language="en"
    )


@app.post("/api/v1/multimodal/chunk", response_model=ChunkResponse)
async def create_chunks(
    request: ChunkRequest,
    x_internal_service_secret: Optional[str] = Header(None, alias="X-Internal-Service-Secret"),
    x_internal_secret: Optional[str] = Header(None, alias="X-Internal-Secret"),
    x_mock_fault: Optional[str] = Header(None, alias="X-Mock-Fault")
):
    verify_secret(x_internal_service_secret, x_internal_secret)
    await check_fault_injection(x_mock_fault)

    raw_text = request.content or request.text or ""
    logger.info("Mock chunking received text of length %d", len(raw_text))

    if not raw_text.strip():
        return ChunkResponse(chunks=[])

    # Generate 2 deterministic chunks
    chunk1 = raw_text[: len(raw_text) // 2 + 10] if len(raw_text) > 20 else raw_text
    chunk2 = raw_text[len(raw_text) // 2 - 10 :] if len(raw_text) > 20 else (raw_text + " (end)")
    return ChunkResponse(chunks=[chunk1, chunk2])
