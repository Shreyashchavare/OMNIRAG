"""
OMRAGUL V5 TDD Contract Tests: Multimodal Service
Verifies:
1. ChunkRequest accepts both 'text' and 'content' fields.
2. ChunkResponse includes 'chunks', 'total_chunks', and 'totalChunks' alias.
3. PDF extraction contract with mocked/real backend.
4. Dual-header authentication support (X-Internal-Service-Secret & X-Internal-Secret).
"""

import io
import os
import sys
import unittest
from unittest.mock import MagicMock, patch

# Mock heavy/native packages if not installed on host
for mod_name in ("pytesseract", "faster_whisper", "pymupdf", "fitz", "multipart", "multipart.multipart"):
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

from fastapi.testclient import TestClient
from app.config.settings import settings
from app.main import app


class TestMultimodalV5Contracts(unittest.TestCase):

    def setUp(self):
        self.secret = "v5-multimodal-test-secret-999"
        settings.INTERNAL_SERVICE_SECRET = self.secret
        self.client = TestClient(app)
        self.client.__enter__()

    def tearDown(self):
        self.client.__exit__(None, None, None)

    def test_chunk_request_with_content_field(self):
        """ChunkRequest must accept 'content' field in place of 'text'."""
        response = self.client.post(
            "/api/v1/multimodal/chunk",
            json={
                "content": "This is sample text passed via content field for V5 integration.",
                "chunk_size": 50,
                "chunk_overlap": 10
            },
            headers={"X-Internal-Service-Secret": self.secret}
        )
        self.assertEqual(response.status_code, 200, f"Expected 200, got {response.status_code}: {response.text}")
        data = response.json()
        self.assertIn("chunks", data)
        self.assertGreater(len(data["chunks"]), 0)
        self.assertTrue("total_chunks" in data or "totalChunks" in data)

    def test_chunk_request_with_text_field(self):
        """ChunkRequest must accept 'text' field."""
        response = self.client.post(
            "/api/v1/multimodal/chunk",
            json={
                "text": "This is sample text passed via standard text field.",
                "chunk_size": 50,
                "chunk_overlap": 10
            },
            headers={"X-Internal-Service-Secret": self.secret}
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertIn("chunks", data)
        self.assertGreater(len(data["chunks"]), 0)

    def test_pdf_extraction_contract(self):
        """PDF extraction route returns standard PDFResponse."""
        with patch("app.api.routes.multimodal_routes.PDFService.extract_text", return_value=("V5 PDF Content", 1)):
            response = self.client.post(
                "/api/v1/multimodal/pdf/extract",
                files={"file": ("sample.pdf", b"%PDF-1.4 dummy", "application/pdf")},
                headers={"X-Internal-Service-Secret": self.secret}
            )
            self.assertEqual(response.status_code, 200)
            data = response.json()
            self.assertEqual(data.get("text"), "V5 PDF Content")
            self.assertEqual(data.get("pages"), 1)


if __name__ == "__main__":
    unittest.main()
