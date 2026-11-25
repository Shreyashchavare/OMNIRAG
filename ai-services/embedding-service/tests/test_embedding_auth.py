"""
Unit and integration tests for Embedding Service authentication:
- Missing secret header -> HTTP 401
- Invalid secret -> HTTP 401
- Valid secret (X-Internal-Service-Secret) -> successful embedding request
- Backward-compatible X-Internal-Secret -> successful embedding request
- Missing server-side secret configuration -> fail closed (HTTP 500)
- Health endpoint -> remains publicly accessible without credentials
- Batch endpoint -> rejects unauthorized calls before model inference
- Valid batch request -> existing functionality preserved
"""

import os
import sys
import unittest
from unittest.mock import ANY, AsyncMock, patch
from fastapi.testclient import TestClient

embedding_service_path = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if embedding_service_path not in sys.path:
    sys.path.insert(0, embedding_service_path)

from app.config.settings import settings
from app.main import app


class TestEmbeddingServiceAuthentication(unittest.TestCase):

    def setUp(self):
        self.secret = "test-cluster-service-secret-777"
        settings.INTERNAL_SERVICE_SECRET = self.secret
        self.client = TestClient(app)
        self.client.__enter__()

    def tearDown(self):
        self.client.__exit__(None, None, None)

    def test_health_endpoint_publicly_accessible(self):
        """GET /health must remain accessible without any authentication headers."""
        with patch("app.main.embedding_service.check_ollama", new_callable=AsyncMock) as mock_check:
            mock_check.return_value = True
            response = self.client.get("/health")
            self.assertEqual(response.status_code, 200)
            data = response.json()
            self.assertEqual(data.get("status"), "UP")
            self.assertEqual(data.get("service"), "embedding-service")

    def test_single_embedding_missing_secret_returns_401(self):
        """POST /api/v1/embeddings without credentials must return HTTP 401."""
        response = self.client.post(
            "/api/v1/embeddings",
            json={"text": "Hello world"}
        )
        self.assertEqual(response.status_code, 401)
        self.assertIn("missing or invalid internal service credentials", response.json().get("detail", ""))

    def test_single_embedding_invalid_secret_returns_401(self):
        """POST /api/v1/embeddings with incorrect secret must return HTTP 401."""
        response = self.client.post(
            "/api/v1/embeddings",
            json={"text": "Hello world"},
            headers={"X-Internal-Service-Secret": "wrong-secret-value"}
        )
        self.assertEqual(response.status_code, 401)
        self.assertIn("missing or invalid internal service credentials", response.json().get("detail", ""))

    def test_single_embedding_valid_secret_success(self):
        """POST /api/v1/embeddings with valid X-Internal-Service-Secret succeeds."""
        mock_emb = [0.123, -0.456, 0.789]
        with patch("app.routes.embedding_routes.embedding_service.generate_embedding", new_callable=AsyncMock) as mock_gen:
            mock_gen.return_value = mock_emb

            response = self.client.post(
                "/api/v1/embeddings",
                json={"text": "Authenticate this text"},
                headers={
                    "X-Internal-Service-Secret": self.secret,
                    "X-Internal-Caller-Service": "rag-service"
                }
            )

            self.assertEqual(response.status_code, 200)
            data = response.json()
            self.assertEqual(data.get("embedding"), mock_emb)
            self.assertEqual(data.get("dimension"), 3)
            mock_gen.assert_called_once_with("Authenticate this text", client=ANY)

    def test_single_embedding_backward_compatible_secret_header(self):
        """POST /api/v1/embeddings with valid X-Internal-Secret succeeds."""
        mock_emb = [0.555, 0.666, 0.777]
        with patch("app.routes.embedding_routes.embedding_service.generate_embedding", new_callable=AsyncMock) as mock_gen:
            mock_gen.return_value = mock_emb

            response = self.client.post(
                "/api/v1/embeddings",
                json={"text": "Legacy header text"},
                headers={"X-Internal-Secret": self.secret}
            )

            self.assertEqual(response.status_code, 200)
            data = response.json()
            self.assertEqual(data.get("embedding"), mock_emb)
            mock_gen.assert_called_once_with("Legacy header text", client=ANY)

    def test_batch_embedding_missing_secret_rejects_before_model_inference(self):
        """POST /api/v1/embeddings/batch without credentials rejects with 401 before inference."""
        with patch("app.routes.embedding_routes.embedding_service.generate_embeddings", new_callable=AsyncMock) as mock_batch_gen:
            response = self.client.post(
                "/api/v1/embeddings/batch",
                json={"texts": ["Text 1", "Text 2"]}
            )

            self.assertEqual(response.status_code, 401)
            # CRITICAL: Verify model inference was NOT executed
            mock_batch_gen.assert_not_called()

    def test_batch_embedding_invalid_secret_rejects_before_model_inference(self):
        """POST /api/v1/embeddings/batch with bad secret rejects with 401 before inference."""
        with patch("app.routes.embedding_routes.embedding_service.generate_embeddings", new_callable=AsyncMock) as mock_batch_gen:
            response = self.client.post(
                "/api/v1/embeddings/batch",
                json={"texts": ["Text 1", "Text 2"]},
                headers={"X-Internal-Service-Secret": "tampered-secret"}
            )

            self.assertEqual(response.status_code, 401)
            mock_batch_gen.assert_not_called()

    def test_batch_embedding_valid_secret_success(self):
        """POST /api/v1/embeddings/batch with valid secret succeeds."""
        mock_embs = [
            [0.1, 0.2, 0.3],
            [0.4, 0.5, 0.6]
        ]
        with patch("app.routes.embedding_routes.embedding_service.generate_embeddings", new_callable=AsyncMock) as mock_batch_gen:
            mock_batch_gen.return_value = mock_embs

            response = self.client.post(
                "/api/v1/embeddings/batch",
                json={"texts": ["Doc A", "Doc B"]},
                headers={"X-Internal-Service-Secret": self.secret}
            )

            self.assertEqual(response.status_code, 200)
            data = response.json()
            self.assertEqual(data.get("embeddings"), mock_embs)
            self.assertEqual(data.get("dimension"), 3)
            mock_batch_gen.assert_called_once_with(["Doc A", "Doc B"], client=ANY)

    def test_missing_server_side_secret_fails_closed_with_500(self):
        """If server-side INTERNAL_SERVICE_SECRET is empty/unconfigured, returns HTTP 500."""
        settings.INTERNAL_SERVICE_SECRET = ""
        try:
            response = self.client.post(
                "/api/v1/embeddings",
                json={"text": "Fail closed test"},
                headers={"X-Internal-Service-Secret": "any-secret"}
            )
            self.assertEqual(response.status_code, 500)
            self.assertIn("Internal service secret not configured", response.json().get("detail", ""))
        finally:
            settings.INTERNAL_SERVICE_SECRET = self.secret


if __name__ == "__main__":
    unittest.main()
