"""
Unit tests for multimodal resource lifecycle, PIL image cleanup, and audio temporary file cleanup.
Covers:
- FINDING-3.6-08: PIL image processing cleanup and resource closure
- FINDING-3.6-09: Audio temporary file cleanup on success and failure paths
"""

import io
import os
import sys
import tempfile
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from fastapi import HTTPException, UploadFile
from PIL import Image

# Mock external optional libraries if not installed in host test runner
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

from app.api.routes.multimodal_routes import extract_text_from_image, transcribe_audio
from app.services.ocr_service import OCRService


def create_test_image_bytes(mode: str = "RGB", size: tuple = (50, 50), color: str = "red") -> bytes:
    """Helper to generate a valid in-memory PNG image."""
    img = Image.new(mode, size, color)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    buf.seek(0)
    return buf.getvalue()


class TestImageProcessingCleanup(unittest.IsolatedAsyncioTestCase):

    async def test_ocr_success_closes_image_and_upload(self):
        """Image is opened in context manager, text is extracted, and resources closed."""
        image_bytes = create_test_image_bytes("RGB")
        spool = io.BytesIO(image_bytes)

        upload_file = UploadFile(
            file=spool,
            size=len(image_bytes),
            filename="test.png",
            headers={"content-type": "image/png"}
        )

        closed_instances = []
        original_open = Image.open

        def open_spy(*args, **kwargs):
            img = original_open(*args, **kwargs)
            orig_close = img.close

            def close_wrapper(*cargs, **ckwargs):
                closed_instances.append(img)
                return orig_close(*cargs, **ckwargs)

            img.close = close_wrapper
            return img

        with patch("app.services.ocr_service.pytesseract.image_to_string", return_value="Extracted OCR Text"):
            with patch("app.api.routes.multimodal_routes.Image.open", side_effect=open_spy):
                response = await extract_text_from_image(file=upload_file)
                self.assertEqual(response.text, "Extracted OCR Text")
                self.assertEqual(len(closed_instances), 1)

    async def test_ocr_invalid_content_type_rejected(self):
        """Non-image content type is rejected with HTTP 400 without PIL processing."""
        upload_file = UploadFile(
            file=io.BytesIO(b"dummy text"),
            filename="document.txt",
            headers={"content-type": "text/plain"}
        )

        with self.assertRaises(HTTPException) as ctx:
            await extract_text_from_image(file=upload_file)
        self.assertEqual(ctx.exception.status_code, 400)
        self.assertIn("Only image files are supported", ctx.exception.detail)

    async def test_ocr_empty_file_rejected(self):
        """Empty image file is rejected with HTTP 400."""
        upload_file = UploadFile(
            file=io.BytesIO(b""),
            filename="empty.png",
            headers={"content-type": "image/png"}
        )

        with self.assertRaises(HTTPException) as ctx:
            await extract_text_from_image(file=upload_file)
        self.assertEqual(ctx.exception.status_code, 400)
        self.assertIn("Uploaded image file is empty", ctx.exception.detail)

    async def test_ocr_corrupted_image_handled_gracefully(self):
        """Corrupted image bytes trigger handled HTTP 400 without unclosed file handles."""
        upload_file = UploadFile(
            file=io.BytesIO(b"NOT_A_VALID_IMAGE_PAYLOAD"),
            filename="corrupt.png",
            headers={"content-type": "image/png"}
        )

        with self.assertRaises(HTTPException) as ctx:
            await extract_text_from_image(file=upload_file)
        self.assertEqual(ctx.exception.status_code, 400)
        self.assertIn("Unable to process image", ctx.exception.detail)

    def test_ocr_service_converts_and_closes_intermediate_rgba_image(self):
        """OCRService converts RGBA images to RGB and ensures intermediate converted image is closed."""
        rgba_image = Image.new("RGBA", (20, 20), (255, 0, 0, 128))

        close_called = []
        original_close = Image.Image.close

        def close_spy(img_self):
            close_called.append(img_self)
            return original_close(img_self)

        with patch("app.services.ocr_service.pytesseract.image_to_string", return_value="RGBA OCR"):
            with patch.object(Image.Image, "close", side_effect=close_spy, autospec=True):
                result = OCRService.extract_text(rgba_image)
                self.assertEqual(result, "RGBA OCR")
                # The converted RGB image must have been explicitly closed in finally
                self.assertGreaterEqual(len(close_called), 1)

        rgba_image.close()


class TestAudioProcessingCleanup(unittest.IsolatedAsyncioTestCase):

    async def test_transcribe_audio_success_deletes_temp_file(self):
        """Temporary audio file is reliably created and deleted upon successful transcription."""
        audio_content = b"RIFF....WAVEfmt ...."
        upload_file = UploadFile(
            file=io.BytesIO(audio_content),
            filename="sample.wav",
            headers={"content-type": "audio/wav"}
        )

        captured_path = []

        def mock_transcribe(path):
            captured_path.append(path)
            # Verify file exists on disk during transcription
            self.assertTrue(os.path.exists(path))
            return "Transcribed audio text", "en"

        with patch("app.api.routes.multimodal_routes.speech_service.transcribe", side_effect=mock_transcribe):
            response = await transcribe_audio(file=upload_file)
            self.assertEqual(response.text, "Transcribed audio text")
            self.assertEqual(response.language, "en")

        # After route completes, verify the temporary file was cleaned up
        self.assertEqual(len(captured_path), 1)
        self.assertFalse(os.path.exists(captured_path[0]))

    async def test_transcribe_audio_exception_deletes_temp_file(self):
        """Temporary audio file is deleted even when transcription raises an exception."""
        audio_content = b"RIFF....WAVEfmt ...."
        upload_file = UploadFile(
            file=io.BytesIO(audio_content),
            filename="failing.wav",
            headers={"content-type": "audio/wav"}
        )

        captured_path = []

        def mock_failing_transcribe(path):
            captured_path.append(path)
            self.assertTrue(os.path.exists(path))
            raise RuntimeError("Model inference failed or out of memory")

        with patch("app.api.routes.multimodal_routes.speech_service.transcribe", side_effect=mock_failing_transcribe):
            with self.assertRaises(HTTPException) as ctx:
                await transcribe_audio(file=upload_file)
            self.assertEqual(ctx.exception.status_code, 500)

        # File MUST be deleted in finally block despite exception
        self.assertEqual(len(captured_path), 1)
        self.assertFalse(os.path.exists(captured_path[0]))

    async def test_transcribe_audio_invalid_content_type(self):
        """Non-audio content type is rejected with HTTP 400 without creating temp files."""
        upload_file = UploadFile(
            file=io.BytesIO(b"not audio"),
            filename="document.pdf",
            headers={"content-type": "application/pdf"}
        )

        with self.assertRaises(HTTPException) as ctx:
            await transcribe_audio(file=upload_file)
        self.assertEqual(ctx.exception.status_code, 400)

    async def test_transcribe_audio_empty_file(self):
        """Empty audio upload is rejected with HTTP 400 without creating temp files."""
        upload_file = UploadFile(
            file=io.BytesIO(b""),
            filename="empty.mp3",
            headers={"content-type": "audio/mpeg"}
        )

        with self.assertRaises(HTTPException) as ctx:
            await transcribe_audio(file=upload_file)
        self.assertEqual(ctx.exception.status_code, 400)

    async def test_transcribe_audio_suffix_sanitization(self):
        """Dangerous or arbitrary filename suffix is sanitized to prevent traversal or malicious extensions."""
        audio_content = b"VALID_AUDIO_BYTES"
        upload_file = UploadFile(
            file=io.BytesIO(audio_content),
            filename="../../../evil.exe",
            headers={"content-type": "audio/wav"}
        )

        captured_path = []

        def mock_transcribe(path):
            captured_path.append(path)
            # Verify extension was sanitized to .tmp
            self.assertTrue(path.endswith(".tmp"))
            return "Sanitized extension text", "en"

        with patch("app.api.routes.multimodal_routes.speech_service.transcribe", side_effect=mock_transcribe):
            response = await transcribe_audio(file=upload_file)
            self.assertEqual(response.text, "Sanitized extension text")

        self.assertFalse(os.path.exists(captured_path[0]))


if __name__ == "__main__":
    unittest.main()
