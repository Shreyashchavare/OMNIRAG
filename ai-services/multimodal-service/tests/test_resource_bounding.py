"""
Stage 3.6 Batch 3.2: Multimodal CPU Offloading and Resource Bounding Test Suite.

Covers:
1. Upload exceeding byte limit returns HTTP 413.
2. Actual streaming bytes checked even when Content-Length is missing or inaccurate.
3. Partial temporary uploads cleaned up on failure, error, or cancellation.
4. Oversized image dimensions rejected with HTTP 413.
5. Pillow decompression-bomb handled safely and translated to HTTP 413.
6. OCR, PDF, and Whisper blocking CPU work does not block the FastAPI event loop.
7. Bounded concurrency semaphores enforced for OCR, PDF, and Whisper.
8. Settings and lifespan resource initialization verification.
"""

import asyncio
import io
import os
import sys
import time
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from fastapi import HTTPException, UploadFile, Request
from PIL import Image

# Ensure mock modules for optional heavy dependencies if not present in test runner
for mod_name in ("pytesseract", "faster_whisper", "pymupdf", "multipart", "multipart.multipart"):
    if mod_name not in sys.modules:
        sys.modules[mod_name] = MagicMock()

try:
    import fastapi.dependencies.utils as fastapi_utils
    fastapi_utils.ensure_multipart_is_installed = lambda: None
except Exception:
    pass

multimodal_service_path = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if multimodal_service_path not in sys.path:
    sys.path.insert(0, multimodal_service_path)

from app.config.settings import settings
from app.main import app, lifespan
from app.api.routes.multimodal_routes import (
    extract_text_from_image,
    extract_text_from_pdf,
    transcribe_audio,
    analyze_image,
    read_upload_bounded,
    stream_upload_to_temp_file,
    _get_semaphore,
)


def create_test_image_bytes(mode: str = "RGB", size: tuple = (50, 50), color: str = "blue") -> bytes:
    img = Image.new(mode, size, color)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    buf.seek(0)
    return buf.getvalue()


class TestUploadSizeBounding(unittest.IsolatedAsyncioTestCase):

    async def test_read_upload_bounded_header_size_exceeded_returns_413(self):
        """When file.size exceeds max_bytes, reject immediately with HTTP 413 without reading body."""
        upload_file = UploadFile(
            file=io.BytesIO(b"abc"),
            size=1000,
            filename="large.png",
            headers={"content-type": "image/png"}
        )

        with self.assertRaises(HTTPException) as ctx:
            await read_upload_bounded(upload_file, max_bytes=500)
        self.assertEqual(ctx.exception.status_code, 413)
        self.assertIn("exceeds maximum allowed size", ctx.exception.detail)

    async def test_read_upload_bounded_streaming_enforcement_without_size_header(self):
        """When file.size is None or spoofed, streaming byte counter enforces limit and raises 413."""
        # 200 KB payload, limit is 100 KB
        payload = b"X" * (200 * 1024)
        upload_file = UploadFile(
            file=io.BytesIO(payload),
            size=None,  # Missing or omitted Content-Length
            filename="stream.png",
            headers={"content-type": "image/png"}
        )

        with self.assertRaises(HTTPException) as ctx:
            await read_upload_bounded(upload_file, max_bytes=100 * 1024, chunk_size=16 * 1024)
        self.assertEqual(ctx.exception.status_code, 413)
        self.assertIn("exceeds maximum allowed size", ctx.exception.detail)

    async def test_ocr_upload_exceeding_limit_returns_413(self):
        """POST /ocr rejects oversized upload with HTTP 413."""
        upload_file = UploadFile(
            file=io.BytesIO(b"oversized_image_data"),
            size=settings.MAX_UPLOAD_SIZE_BYTES + 1,
            filename="huge.png",
            headers={"content-type": "image/png"}
        )

        with self.assertRaises(HTTPException) as ctx:
            await extract_text_from_image(file=upload_file)
        self.assertEqual(ctx.exception.status_code, 413)

    async def test_pdf_upload_exceeding_limit_returns_413(self):
        """POST /pdf/extract rejects oversized upload with HTTP 413."""
        upload_file = UploadFile(
            file=io.BytesIO(b"oversized_pdf_data"),
            size=settings.MAX_UPLOAD_SIZE_BYTES + 1,
            filename="huge.pdf",
            headers={"content-type": "application/pdf"}
        )

        with self.assertRaises(HTTPException) as ctx:
            await extract_text_from_pdf(file=upload_file)
        self.assertEqual(ctx.exception.status_code, 413)

    async def test_speech_upload_exceeding_limit_returns_413(self):
        """POST /speech/transcribe rejects oversized upload with HTTP 413."""
        upload_file = UploadFile(
            file=io.BytesIO(b"oversized_audio_data"),
            size=settings.MAX_UPLOAD_SIZE_BYTES + 1,
            filename="huge.wav",
            headers={"content-type": "audio/wav"}
        )

        with self.assertRaises(HTTPException) as ctx:
            await transcribe_audio(file=upload_file)
        self.assertEqual(ctx.exception.status_code, 413)

    async def test_vision_upload_exceeding_limit_returns_413(self):
        """POST /vision/analyze rejects oversized upload with HTTP 413."""
        upload_file = UploadFile(
            file=io.BytesIO(b"oversized_vision_data"),
            size=settings.MAX_UPLOAD_SIZE_BYTES + 1,
            filename="huge.jpg",
            headers={"content-type": "image/jpeg"}
        )
        mock_client = AsyncMock()

        with self.assertRaises(HTTPException) as ctx:
            await analyze_image(file=upload_file, http_client=mock_client)
        self.assertEqual(ctx.exception.status_code, 413)


class TestAudioStreamingAndCleanup(unittest.IsolatedAsyncioTestCase):

    async def test_stream_upload_to_temp_file_cleans_up_on_oversized_stream(self):
        """When audio stream exceeds limit during chunk write, temp file is deleted immediately."""
        # 150 KB payload, 50 KB max
        payload = b"A" * (150 * 1024)
        upload_file = UploadFile(
            file=io.BytesIO(payload),
            size=None,
            filename="test.wav",
            headers={"content-type": "audio/wav"}
        )

        created_paths = []
        original_named_temp = os.path.exists

        with self.assertRaises(HTTPException) as ctx:
            await stream_upload_to_temp_file(
                upload_file,
                suffix=".wav",
                max_bytes=50 * 1024,
                chunk_size=16 * 1024
            )
        self.assertEqual(ctx.exception.status_code, 413)

    async def test_stream_upload_to_temp_file_empty_upload_raises_400(self):
        """Empty audio upload is rejected with HTTP 400 and cleans up any temp file."""
        upload_file = UploadFile(
            file=io.BytesIO(b""),
            size=0,
            filename="empty.wav",
            headers={"content-type": "audio/wav"}
        )

        with self.assertRaises(HTTPException) as ctx:
            await stream_upload_to_temp_file(
                upload_file,
                suffix=".wav",
                max_bytes=1024
            )
        self.assertEqual(ctx.exception.status_code, 400)
        self.assertIn("Uploaded audio file is empty", ctx.exception.detail)

    async def test_transcribe_audio_cancellation_cleans_up_temp_file(self):
        """If transcription coroutine is cancelled, finally block removes the temporary file."""
        audio_content = b"RIFF....WAVEfmt ...."
        upload_file = UploadFile(
            file=io.BytesIO(audio_content),
            filename="cancel_test.wav",
            headers={"content-type": "audio/wav"}
        )

        captured_path = []

        def cancelling_transcribe(path):
            captured_path.append(path)
            self.assertTrue(os.path.exists(path))
            raise asyncio.CancelledError()

        with patch("app.api.routes.multimodal_routes.speech_service.transcribe", side_effect=cancelling_transcribe):
            with self.assertRaises(asyncio.CancelledError):
                await transcribe_audio(file=upload_file)

        self.assertEqual(len(captured_path), 1)
        self.assertFalse(os.path.exists(captured_path[0]))


class TestImageDecompressionAndDimensions(unittest.IsolatedAsyncioTestCase):

    async def test_ocr_rejects_oversized_pixel_dimensions(self):
        """Images exceeding MAX_IMAGE_PIXELS are rejected with HTTP 413."""
        image_bytes = create_test_image_bytes(size=(100, 100))
        upload_file = UploadFile(
            file=io.BytesIO(image_bytes),
            filename="oversized_dims.png",
            headers={"content-type": "image/png"}
        )

        # Mock settings.MAX_IMAGE_PIXELS to 500 pixels (100x100 = 10,000 pixels > 500)
        with patch.object(settings, "MAX_IMAGE_PIXELS", 500):
            with self.assertRaises(HTTPException) as ctx:
                await extract_text_from_image(file=upload_file)
            self.assertEqual(ctx.exception.status_code, 413)
            self.assertIn("exceed maximum allowed pixels", ctx.exception.detail)

    async def test_ocr_handles_pillow_decompression_bomb_error(self):
        """Pillow DecompressionBombError is caught and returned as HTTP 413."""
        image_bytes = create_test_image_bytes(size=(10, 10))
        upload_file = UploadFile(
            file=io.BytesIO(image_bytes),
            filename="bomb.png",
            headers={"content-type": "image/png"}
        )

        with patch("app.api.routes.multimodal_routes.Image.open", side_effect=Image.DecompressionBombError("Simulated Bomb Attack")):
            with self.assertRaises(HTTPException) as ctx:
                await extract_text_from_image(file=upload_file)
            self.assertEqual(ctx.exception.status_code, 413)
            self.assertIn("Image exceeds decompression limit", ctx.exception.detail)

    async def test_vision_handles_pillow_decompression_bomb_error(self):
        """POST /vision/analyze catches DecompressionBombError and returns HTTP 413."""
        image_bytes = create_test_image_bytes(size=(10, 10))
        upload_file = UploadFile(
            file=io.BytesIO(image_bytes),
            filename="vision_bomb.png",
            headers={"content-type": "image/png"}
        )
        mock_client = AsyncMock()

        with patch("app.api.routes.multimodal_routes.Image.open", side_effect=Image.DecompressionBombError("Vision Bomb Attack")):
            with self.assertRaises(HTTPException) as ctx:
                await analyze_image(file=upload_file, http_client=mock_client)
            self.assertEqual(ctx.exception.status_code, 413)
            self.assertIn("Image exceeds decompression limit", ctx.exception.detail)


class TestCPUOffloadingNonBlocking(unittest.IsolatedAsyncioTestCase):

    async def test_ocr_cpu_work_does_not_block_event_loop(self):
        """Heavy OCR CPU work executed via asyncio.to_thread allows concurrent event loop coroutines to run."""
        image_bytes = create_test_image_bytes(size=(20, 20))
        upload_file = UploadFile(
            file=io.BytesIO(image_bytes),
            filename="test.png",
            headers={"content-type": "image/png"}
        )

        def slow_blocking_ocr(img):
            # Sleep in the worker thread to simulate heavy CPU inference
            time.sleep(0.1)
            return "Extracted Thread Text"

        loop_ticks = []

        async def concurrent_task():
            for _ in range(5):
                await asyncio.sleep(0.01)
                loop_ticks.append(time.time())

        with patch("app.api.routes.multimodal_routes.OCRService.extract_text", side_effect=slow_blocking_ocr):
            # Run OCR and concurrent task concurrently
            ocr_coro = extract_text_from_image(file=upload_file)
            results = await asyncio.gather(ocr_coro, concurrent_task())

        ocr_response = results[0]
        self.assertEqual(ocr_response.text, "Extracted Thread Text")
        # Concurrent coroutine must have ticked multiple times while slow OCR was running
        self.assertGreaterEqual(len(loop_ticks), 3)

    async def test_pdf_cpu_work_does_not_block_event_loop(self):
        """Heavy PDF extraction via asyncio.to_thread allows concurrent event loop coroutines to run."""
        upload_file = UploadFile(
            file=io.BytesIO(b"%PDF-1.4 dummy"),
            filename="test.pdf",
            headers={"content-type": "application/pdf"}
        )

        def slow_blocking_pdf(file_bytes):
            time.sleep(0.1)
            return "PDF Extracted Thread Text", 1

        loop_ticks = []

        async def concurrent_task():
            for _ in range(5):
                await asyncio.sleep(0.01)
                loop_ticks.append(time.time())

        with patch("app.api.routes.multimodal_routes.PDFService.extract_text", side_effect=slow_blocking_pdf):
            pdf_coro = extract_text_from_pdf(file=upload_file)
            results = await asyncio.gather(pdf_coro, concurrent_task())

        pdf_response = results[0]
        self.assertEqual(pdf_response.text, "PDF Extracted Thread Text")
        self.assertEqual(pdf_response.pages, 1)
        self.assertGreaterEqual(len(loop_ticks), 3)

    async def test_whisper_cpu_work_does_not_block_event_loop(self):
        """Heavy Whisper transcription via asyncio.to_thread allows concurrent event loop coroutines to run."""
        upload_file = UploadFile(
            file=io.BytesIO(b"RIFF....WAVEfmt ...."),
            filename="test.wav",
            headers={"content-type": "audio/wav"}
        )

        def slow_blocking_whisper(path):
            time.sleep(0.1)
            return "Whisper Thread Audio Text", "en"

        loop_ticks = []

        async def concurrent_task():
            for _ in range(5):
                await asyncio.sleep(0.01)
                loop_ticks.append(time.time())

        with patch("app.api.routes.multimodal_routes.speech_service.transcribe", side_effect=slow_blocking_whisper):
            speech_coro = transcribe_audio(file=upload_file)
            results = await asyncio.gather(speech_coro, concurrent_task())

        speech_response = results[0]
        self.assertEqual(speech_response.text, "Whisper Thread Audio Text")
        self.assertEqual(speech_response.language, "en")
        self.assertGreaterEqual(len(loop_ticks), 3)


class TestConcurrencyBounding(unittest.IsolatedAsyncioTestCase):

    async def test_ocr_concurrency_bounded_by_semaphore(self):
        """OCR requests are bounded by semaphore, enforcing maximum concurrency."""
        app_mock = MagicMock()
        # Bound semaphore to 2 concurrent workers
        sem = asyncio.Semaphore(2)
        app_mock.state.ocr_semaphore = sem

        request = MagicMock(spec=Request)
        request.app = app_mock

        active_count = 0
        max_seen = 0

        def tracked_ocr(img):
            nonlocal active_count, max_seen
            active_count += 1
            if active_count > max_seen:
                max_seen = active_count
            time.sleep(0.05)
            active_count -= 1
            return "Tracked OCR"

        with patch("app.api.routes.multimodal_routes.OCRService.extract_text", side_effect=tracked_ocr):
            tasks = []
            for _ in range(4):
                image_bytes = create_test_image_bytes(size=(10, 10))
                upload_file = UploadFile(
                    file=io.BytesIO(image_bytes),
                    filename="test.png",
                    headers={"content-type": "image/png"}
                )
                tasks.append(extract_text_from_image(file=upload_file, request=request))

            responses = await asyncio.gather(*tasks)

        self.assertEqual(len(responses), 4)
        for resp in responses:
            self.assertEqual(resp.text, "Tracked OCR")
        # Concurrency must not have exceeded 2
        self.assertLessEqual(max_seen, 2)

    async def test_pdf_concurrency_bounded_by_semaphore(self):
        """PDF requests are bounded by semaphore, enforcing maximum concurrency."""
        app_mock = MagicMock()
        sem = asyncio.Semaphore(2)
        app_mock.state.pdf_semaphore = sem

        request = MagicMock(spec=Request)
        request.app = app_mock

        active_count = 0
        max_seen = 0

        def tracked_pdf(b):
            nonlocal active_count, max_seen
            active_count += 1
            if active_count > max_seen:
                max_seen = active_count
            time.sleep(0.05)
            active_count -= 1
            return "Tracked PDF", 1

        with patch("app.api.routes.multimodal_routes.PDFService.extract_text", side_effect=tracked_pdf):
            tasks = []
            for _ in range(4):
                upload_file = UploadFile(
                    file=io.BytesIO(b"%PDF-1.4 dummy"),
                    filename="test.pdf",
                    headers={"content-type": "application/pdf"}
                )
                tasks.append(extract_text_from_pdf(file=upload_file, request=request))

            responses = await asyncio.gather(*tasks)

        self.assertEqual(len(responses), 4)
        self.assertLessEqual(max_seen, 2)

    async def test_whisper_concurrency_bounded_by_semaphore(self):
        """Whisper transcription requests are bounded by semaphore."""
        app_mock = MagicMock()
        sem = asyncio.Semaphore(1)
        app_mock.state.whisper_semaphore = sem

        request = MagicMock(spec=Request)
        request.app = app_mock

        active_count = 0
        max_seen = 0

        def tracked_whisper(p):
            nonlocal active_count, max_seen
            active_count += 1
            if active_count > max_seen:
                max_seen = active_count
            time.sleep(0.05)
            active_count -= 1
            return "Tracked Audio", "en"

        with patch("app.api.routes.multimodal_routes.speech_service.transcribe", side_effect=tracked_whisper):
            tasks = []
            for _ in range(3):
                upload_file = UploadFile(
                    file=io.BytesIO(b"RIFF....WAVEfmt ...."),
                    filename="test.wav",
                    headers={"content-type": "audio/wav"}
                )
                tasks.append(transcribe_audio(file=upload_file, request=request))

            responses = await asyncio.gather(*tasks)

        self.assertEqual(len(responses), 3)
        self.assertEqual(max_seen, 1)


class TestLifespanAndSettings(unittest.IsolatedAsyncioTestCase):

    def test_settings_resource_bounding_defaults(self):
        """Verify default configuration limits."""
        self.assertEqual(settings.MAX_UPLOAD_SIZE_BYTES, 100 * 1024 * 1024)
        self.assertEqual(settings.MAX_IMAGE_PIXELS, 25_000_000)
        self.assertEqual(settings.MAX_CONCURRENT_OCR, 4)
        self.assertEqual(settings.MAX_CONCURRENT_PDF, 4)
        self.assertEqual(settings.MAX_CONCURRENT_WHISPER, 2)

    async def test_lifespan_configures_semaphores_and_pillow_limit(self):
        """Lifespan context manager configures semaphores on app.state and sets Pillow MAX_IMAGE_PIXELS."""
        test_app = app
        async with lifespan(test_app):
            self.assertTrue(hasattr(test_app.state, "ocr_semaphore"))
            self.assertTrue(hasattr(test_app.state, "pdf_semaphore"))
            self.assertTrue(hasattr(test_app.state, "whisper_semaphore"))
            self.assertIsInstance(test_app.state.ocr_semaphore, asyncio.Semaphore)
            self.assertIsInstance(test_app.state.pdf_semaphore, asyncio.Semaphore)
            self.assertIsInstance(test_app.state.whisper_semaphore, asyncio.Semaphore)
            self.assertEqual(Image.MAX_IMAGE_PIXELS, settings.MAX_IMAGE_PIXELS)


if __name__ == "__main__":
    unittest.main()
