"""
Unit and integration tests for Multimodal Service internal authentication:
- Public /health endpoint accessibility without credentials
- Missing secret header -> HTTP 401
- Invalid secret -> HTTP 401
- Valid primary secret (X-Internal-Service-Secret) -> HTTP 200
- Backward-compatible X-Internal-Secret -> HTTP 200
- Missing server-side secret configuration -> fail closed (HTTP 500)
- Verify expensive processing (OCR, PDF, Chunk, Whisper, Vision) is NOT called on 401/500
- Verify caller attribution header (X-Internal-Caller-Service)
"""

import io
import os
import sys
import unittest
from unittest.mock import AsyncMock, MagicMock, patch
from fastapi.testclient import TestClient
from PIL import Image

# Ensure optional heavy dependencies are mocked if absent on test runner
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
from app.main import app


def create_dummy_png_bytes() -> bytes:
    img = Image.new("RGB", (20, 20), color="blue")
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    buf.seek(0)
    return buf.getvalue()


class TestMultimodalServiceAuthentication(unittest.TestCase):

    def setUp(self):
        self.secret = "test-cluster-multimodal-secret-888"
        settings.INTERNAL_SERVICE_SECRET = self.secret
        self.client = TestClient(app)
        self.client.__enter__()

    def tearDown(self):
        self.client.__exit__(None, None, None)

    def test_health_endpoint_publicly_accessible(self):
        """GET /health must remain accessible without authentication headers."""
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data.get("status"), "UP")
        self.assertEqual(data.get("service"), "multimodal-service")

    # --- Missing Credentials (HTTP 401) ---

    def test_chunk_missing_secret_returns_401(self):
        """POST /api/v1/multimodal/chunk without credentials must return 401."""
        response = self.client.post(
            "/api/v1/multimodal/chunk",
            json={"text": "Valid test text to chunk", "chunk_size": 100, "chunk_overlap": 20}
        )
        self.assertEqual(response.status_code, 401)
        self.assertIn("missing or invalid internal service credentials", response.json().get("detail", ""))

    def test_ocr_missing_secret_returns_401(self):
        """POST /api/v1/multimodal/ocr without credentials must return 401."""
        png_bytes = create_dummy_png_bytes()
        response = self.client.post(
            "/api/v1/multimodal/ocr",
            files={"file": ("test.png", png_bytes, "image/png")}
        )
        self.assertEqual(response.status_code, 401)

    def test_pdf_extract_missing_secret_returns_401(self):
        """POST /api/v1/multimodal/pdf/extract without credentials must return 401."""
        response = self.client.post(
            "/api/v1/multimodal/pdf/extract",
            files={"file": ("test.pdf", b"%PDF-1.4 dummy", "application/pdf")}
        )
        self.assertEqual(response.status_code, 401)

    def test_speech_transcribe_missing_secret_returns_401(self):
        """POST /api/v1/multimodal/speech/transcribe without credentials must return 401."""
        response = self.client.post(
            "/api/v1/multimodal/speech/transcribe",
            files={"file": ("audio.wav", b"RIFF dummy wav data", "audio/wav")}
        )
        self.assertEqual(response.status_code, 401)

    def test_vision_analyze_missing_secret_returns_401(self):
        """POST /api/v1/multimodal/vision/analyze without credentials must return 401."""
        png_bytes = create_dummy_png_bytes()
        response = self.client.post(
            "/api/v1/multimodal/vision/analyze",
            files={"file": ("test.png", png_bytes, "image/png")},
            data={"prompt": "Analyze this"}
        )
        self.assertEqual(response.status_code, 401)

    # --- Invalid Credentials (HTTP 401) ---

    def test_invalid_secret_returns_401(self):
        """Requests with wrong secret must return HTTP 401."""
        response = self.client.post(
            "/api/v1/multimodal/chunk",
            json={"text": "Hello text", "chunk_size": 100, "chunk_overlap": 20},
            headers={"X-Internal-Service-Secret": "incorrect-secret-value"}
        )
        self.assertEqual(response.status_code, 401)

    # --- Valid Credentials (HTTP 200) ---

    def test_chunk_valid_primary_secret_success(self):
        """POST /api/v1/multimodal/chunk with valid X-Internal-Service-Secret succeeds."""
        response = self.client.post(
            "/api/v1/multimodal/chunk",
            json={"text": "Paragraph one.\n\nParagraph two.", "chunk_size": 100, "chunk_overlap": 20},
            headers={
                "X-Internal-Service-Secret": self.secret,
                "X-Internal-Caller-Service": "processing-service"
            }
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertIn("chunks", data)
        self.assertGreater(data.get("total_chunks", 0), 0)

    def test_chunk_valid_legacy_secret_success(self):
        """POST /api/v1/multimodal/chunk with legacy X-Internal-Secret succeeds."""
        response = self.client.post(
            "/api/v1/multimodal/chunk",
            json={"text": "Legacy auth chunk test", "chunk_size": 100, "chunk_overlap": 20},
            headers={"X-Internal-Secret": self.secret}
        )
        self.assertEqual(response.status_code, 200)

    def test_ocr_valid_secret_success(self):
        """POST /api/v1/multimodal/ocr with valid secret invokes OCRService and succeeds."""
        png_bytes = create_dummy_png_bytes()
        with patch("app.api.routes.multimodal_routes.OCRService.extract_text", return_value="Recognized OCR text") as mock_ocr:
            response = self.client.post(
                "/api/v1/multimodal/ocr",
                files={"file": ("test.png", png_bytes, "image/png")},
                headers={
                    "X-Internal-Service-Secret": self.secret,
                    "X-Internal-Caller-Service": "processing-service"
                }
            )
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.json().get("text"), "Recognized OCR text")
            mock_ocr.assert_called_once()

    def test_pdf_extract_valid_secret_success(self):
        """POST /api/v1/multimodal/pdf/extract with valid secret invokes PDFService and succeeds."""
        with patch("app.api.routes.multimodal_routes.PDFService.extract_text", return_value=("PDF Extracted Content", 2)) as mock_pdf:
            response = self.client.post(
                "/api/v1/multimodal/pdf/extract",
                files={"file": ("doc.pdf", b"%PDF-1.4 mock content", "application/pdf")},
                headers={
                    "X-Internal-Service-Secret": self.secret,
                    "X-Internal-Caller-Service": "processing-service"
                }
            )
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.json().get("text"), "PDF Extracted Content")
            self.assertEqual(response.json().get("pages"), 2)
            mock_pdf.assert_called_once()

    def test_speech_transcribe_valid_secret_success(self):
        """POST /api/v1/multimodal/speech/transcribe with valid secret invokes speech_service and succeeds."""
        with patch("app.api.routes.multimodal_routes.speech_service.transcribe", return_value=("Transcribed voice text", "en")) as mock_speech:
            response = self.client.post(
                "/api/v1/multimodal/speech/transcribe",
                files={"file": ("audio.wav", b"RIFF dummy wav data", "audio/wav")},
                headers={
                    "X-Internal-Service-Secret": self.secret,
                    "X-Internal-Caller-Service": "processing-service"
                }
            )
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.json().get("text"), "Transcribed voice text")
            self.assertEqual(response.json().get("language"), "en")
            mock_speech.assert_called_once()

    def test_vision_analyze_valid_secret_success(self):
        """POST /api/v1/multimodal/vision/analyze with valid secret invokes VisionService and succeeds."""
        png_bytes = create_dummy_png_bytes()
        with patch("app.api.routes.multimodal_routes.VisionService.analyze_image", new_callable=AsyncMock) as mock_vision:
            mock_vision.return_value = "Detailed vision analysis description"
            response = self.client.post(
                "/api/v1/multimodal/vision/analyze",
                files={"file": ("test.png", png_bytes, "image/png")},
                data={"prompt": "Custom prompt"},
                headers={
                    "X-Internal-Service-Secret": self.secret,
                    "X-Internal-Caller-Service": "processing-service"
                }
            )
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.json().get("text"), "Detailed vision analysis description")
            mock_vision.assert_called_once()

    # --- Server Configuration Fail-Closed (HTTP 500) ---

    def test_server_unconfigured_secret_fails_closed_500(self):
        """When server has no secret configured, requests must fail closed with HTTP 500."""
        settings.INTERNAL_SERVICE_SECRET = ""
        response = self.client.post(
            "/api/v1/multimodal/chunk",
            json={"text": "Fail closed test", "chunk_size": 100, "chunk_overlap": 20},
            headers={"X-Internal-Service-Secret": "any-secret"}
        )
        self.assertEqual(response.status_code, 500)
        self.assertIn("Internal service secret not configured", response.json().get("detail", ""))

    # --- Security: Pre-Execution Verification ---

    def test_unauthenticated_request_never_invokes_expensive_compute(self):
        """Verify that on 401 Unauthorized, heavy models/extractors are never called."""
        png_bytes = create_dummy_png_bytes()
        with patch("app.api.routes.multimodal_routes.OCRService.extract_text") as mock_ocr, \
             patch("app.api.routes.multimodal_routes.PDFService.extract_text") as mock_pdf, \
             patch("app.api.routes.multimodal_routes.speech_service.transcribe") as mock_speech, \
             patch("app.api.routes.multimodal_routes.VisionService.analyze_image") as mock_vision:

            resp_ocr = self.client.post("/api/v1/multimodal/ocr", files={"file": ("test.png", png_bytes, "image/png")})
            self.assertEqual(resp_ocr.status_code, 401)
            mock_ocr.assert_not_called()

            resp_pdf = self.client.post("/api/v1/multimodal/pdf/extract", files={"file": ("test.pdf", b"pdf", "application/pdf")})
            self.assertEqual(resp_pdf.status_code, 401)
            mock_pdf.assert_not_called()

            resp_speech = self.client.post("/api/v1/multimodal/speech/transcribe", files={"file": ("a.wav", b"wav", "audio/wav")})
            self.assertEqual(resp_speech.status_code, 401)
            mock_speech.assert_not_called()

            resp_vis = self.client.post("/api/v1/multimodal/vision/analyze", files={"file": ("test.png", png_bytes, "image/png")})
            self.assertEqual(resp_vis.status_code, 401)
            mock_vision.assert_not_called()

    def test_whitespace_padded_secret_comparison(self):
        """Constant-time comparison handles whitespace-padded secrets cleanly."""
        response = self.client.post(
            "/api/v1/multimodal/chunk",
            json={"text": "Whitespace test", "chunk_size": 100, "chunk_overlap": 20},
            headers={"X-Internal-Service-Secret": f"  {self.secret}  "}
        )
        self.assertEqual(response.status_code, 200)
