import os
import tempfile

from fastapi import APIRouter, File, HTTPException, UploadFile, Form
from PIL import Image

from app.models.schemas import OCRResponse
from app.services.ocr_service import OCRService

from app.models.schemas import PDFResponse
from app.services.pdf_service import PDFService

from app.models.schemas import ChunkResponse
from app.services.chunk_service import ChunkService

from app.models.schemas import SpeechResponse
from app.services.speech_service import speech_service

from app.services.vision_service import VisionService


router = APIRouter(
    prefix="/api/v1/multimodal",
    tags=["Multimodal"]
)


@router.post("/ocr", response_model=OCRResponse)
async def extract_text_from_image(
    file: UploadFile = File(...)
):

    try:
        image = Image.open(file.file)

        text = OCRService.extract_text(image)

        return OCRResponse(
            text=text
        )

    except Exception as e:
        raise HTTPException(
            status_code=400,
            detail=f"Unable to process image: {str(e)}"
        )


@router.post("/pdf/extract", response_model=PDFResponse)
async def extract_text_from_pdf(
    file: UploadFile = File(...)
):
    if file.content_type != "application/pdf":
        raise HTTPException(
            status_code=400,
            detail="Only PDF files are supported"
        )

    try:
        file_bytes = await file.read()

        if not file_bytes:
            raise HTTPException(
                status_code=400,
                detail="Uploaded PDF is empty"
            )

        text, page_count = PDFService.extract_text(file_bytes)

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


@router.post("/chunk", response_model=ChunkResponse)
async def create_chunks(
    text: str
):
    try:
        chunks = ChunkService.create_chunks(text)

        return ChunkResponse(
            chunks=chunks,
            total_chunks=len(chunks)
        )

    except ValueError as e:
        raise HTTPException(
            status_code=400,
            detail=str(e)
        )

    except Exception:
        raise HTTPException(
            status_code=500,
            detail="Unable to create text chunks"
        )

@router.post("/speech/transcribe", response_model=SpeechResponse)
async def transcribe_audio(
    file: UploadFile = File(...)
):
    if not file.content_type or not file.content_type.startswith("audio/"):
        raise HTTPException(
            status_code=400,
            detail="Only audio files are supported"
        )

    temp_path = None

    try:
        audio_bytes = await file.read()

        if not audio_bytes:
            raise HTTPException(
                status_code=400,
                detail="Uploaded audio file is empty"
            )

        suffix = os.path.splitext(file.filename or "")[1]

        with tempfile.NamedTemporaryFile(
            delete=False,
            suffix=suffix
        ) as temp_file:

            temp_file.write(audio_bytes)
            temp_path = temp_file.name

        text, language = speech_service.transcribe(temp_path)

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

    except Exception:
        raise HTTPException(
            status_code=500,
            detail="Unable to transcribe audio"
        )

    finally:
        if temp_path and os.path.exists(temp_path):
            os.remove(temp_path)


@router.post("/vision/analyze")
async def analyze_image(
    file: UploadFile = File(...),
    prompt: str = Form(
        "Describe this image in detail. "
        "Extract all important information that could be useful for a RAG system."
    )
):
    try:

        if not file.content_type or not file.content_type.startswith("image/"):
            raise HTTPException(
                status_code=400,
                detail="Only image files are supported"
            )

        image_bytes = await file.read()

        result = await VisionService.analyze_image(
            image_bytes=image_bytes,
            prompt=prompt
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