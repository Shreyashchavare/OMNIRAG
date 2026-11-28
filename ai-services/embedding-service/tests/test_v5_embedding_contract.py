"""
OMRAGUL V5 TDD Contract Tests: Embedding Service
Verifies:
1. EmbeddingRequest accepts both 'text' and 'input' fields.
2. BatchEmbeddingRequest accepts both 'texts' and 'input' fields.
3. Fallback deterministic 1024-dim embedding generation when OLLAMA_FALLBACK_EMBEDDINGS=True explicitly.
4. Safe failure with HTTP 502 when OLLAMA_FALLBACK_EMBEDDINGS=False and Ollama is unreachable.
5. Internal service authentication header acceptance.
"""

import os
import sys
import unittest
from unittest.mock import AsyncMock, patch

embedding_service_path = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if embedding_service_path not in sys.path:
    sys.path.insert(0, embedding_service_path)

from fastapi.testclient import TestClient
from app.config.settings import settings
from app.main import app


class TestEmbeddingV5Contracts(unittest.TestCase):

    def setUp(self):
        self.secret = "v5-embedding-test-secret-777"
        settings.INTERNAL_SERVICE_SECRET = self.secret
        settings.OLLAMA_FALLBACK_EMBEDDINGS = True
        self.client = TestClient(app)
        self.client.__enter__()

    def tearDown(self):
        self.client.__exit__(None, None, None)

    def test_single_embedding_with_input_field(self):
        """EmbeddingRequest should accept 'input' in place of 'text'."""
        mock_vec = [0.1] * 1024
        with patch("app.routes.embedding_routes.embedding_service.generate_embedding", new_callable=AsyncMock) as mock_gen:
            mock_gen.return_value = mock_vec
            response = self.client.post(
                "/api/v1/embeddings",
                json={"input": "Hello world from input field"},
                headers={"X-Internal-Service-Secret": self.secret}
            )
            self.assertEqual(response.status_code, 200, f"Expected 200, got {response.status_code}: {response.text}")
            data = response.json()
            self.assertEqual(data.get("dimension"), 1024)

    def test_batch_embedding_with_input_field(self):
        """BatchEmbeddingRequest should accept 'input' in place of 'texts'."""
        mock_vecs = [[0.1] * 1024, [0.2] * 1024]
        with patch("app.routes.embedding_routes.embedding_service.generate_embeddings", new_callable=AsyncMock) as mock_gen:
            mock_gen.return_value = mock_vecs
            response = self.client.post(
                "/api/v1/embeddings/batch",
                json={"input": ["First text passage", "Second text passage"]},
                headers={"X-Internal-Service-Secret": self.secret}
            )
            self.assertEqual(response.status_code, 200, f"Expected 200, got {response.status_code}: {response.text}")
            data = response.json()
            self.assertEqual(len(data.get("embeddings", [])), 2)
            self.assertEqual(data.get("dimension"), 1024)

    def test_deterministic_embedding_generation_fallback_enabled(self):
        """When fallback is enabled explicitly, deterministic 1024-dim normalized vectors are returned."""
        settings.OLLAMA_FALLBACK_EMBEDDINGS = True
        response = self.client.post(
            "/api/v1/embeddings/batch",
            json={"texts": ["OMRAGUL architecture documentation", "Confidential beta project"]},
            headers={"X-Internal-Service-Secret": self.secret}
        )
        self.assertEqual(response.status_code, 200, f"Expected 200, got {response.status_code}: {response.text}")
        data = response.json()
        embeddings = data.get("embeddings", [])
        self.assertEqual(len(embeddings), 2)
        self.assertEqual(data.get("dimension"), 1024)
        self.assertEqual(len(embeddings[0]), 1024)
        self.assertEqual(len(embeddings[1]), 1024)

    def test_fallback_disabled_fails_safely_with_502(self):
        """When fallback is disabled and Ollama is unreachable, service must fail closed with HTTP 502."""
        settings.OLLAMA_FALLBACK_EMBEDDINGS = False
        response = self.client.post(
            "/api/v1/embeddings/batch",
            json={"texts": ["Should fail closed when fallback is disabled"]},
            headers={"X-Internal-Service-Secret": self.secret}
        )
        self.assertEqual(response.status_code, 502, f"Expected 502 Bad Gateway, got {response.status_code}: {response.text}")
        data = response.json()
        self.assertIn("Unable to connect to Ollama", data.get("detail", ""))


if __name__ == "__main__":
    unittest.main()
