"""
Tests for EmbeddingClient batch embedding functionality in rag-service.
Uses standard library unittest and unittest.mock.
"""

import sys
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

# Provide mocks only for modules that cannot be imported
for mod_name in ["httpx", "faiss", "numpy", "pydantic", "pydantic_settings", "fastapi"]:
    if mod_name not in sys.modules:
        try:
            __import__(mod_name)
        except ImportError:
            mock_mod = MagicMock()
            if mod_name == "httpx":
                mock_mod.HTTPError = Exception
                mock_mod.HTTPStatusError = Exception
            sys.modules[mod_name] = mock_mod

# Ensure rag-service is in sys.path
import os
rag_service_path = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if rag_service_path not in sys.path:
    sys.path.insert(0, rag_service_path)

from app.services.embedding_client import EmbeddingClient
from app.config.settings import settings


class TestEmbeddingClientBatch(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        # Configure settings for test
        settings.embedding_service_url = "http://localhost:8001"
        settings.embedding_dimension = 3
        settings.internal_service_secret = "test-rag-secret"

    async def test_generate_single_embedding_success_header(self):
        """Single embedding request must carry X-Internal-Service-Secret."""
        mock_response = MagicMock()
        mock_response.json.return_value = {
            "embedding": [0.1, 0.2, 0.3]
        }
        mock_response.raise_for_status = MagicMock()

        mock_client = AsyncMock()
        mock_client.post = AsyncMock(return_value=mock_response)

        with patch("httpx.AsyncClient") as mock_client_cls:
            mock_client_cls.return_value.__aenter__.return_value = mock_client
            emb = await EmbeddingClient.generate_embedding("Single text")

            self.assertEqual(emb, [0.1, 0.2, 0.3])
            mock_client.post.assert_called_once_with(
                "http://localhost:8001/api/v1/embeddings",
                json={"text": "Single text"},
                headers={
                    "X-Internal-Service-Secret": "test-rag-secret",
                    "X-Internal-Caller-Service": "rag-service"
                }
            )

    async def test_missing_secret_configuration_fails_closed(self):
        """Missing INTERNAL_SERVICE_SECRET fails closed without making an HTTP request."""
        settings.internal_service_secret = ""
        mock_client = AsyncMock()

        try:
            with self.assertRaises(ValueError) as ctx:
                await EmbeddingClient.generate_embedding("Text")
            self.assertIn("INTERNAL_SERVICE_SECRET is not configured", str(ctx.exception))

            with self.assertRaises(ValueError) as ctx2:
                await EmbeddingClient.generate_batch_embeddings(["Text 1"], client=mock_client)
            self.assertIn("INTERNAL_SERVICE_SECRET is not configured", str(ctx2.exception))
            mock_client.post.assert_not_called()
        finally:
            settings.internal_service_secret = "test-rag-secret"

    async def test_generate_batch_embeddings_empty_input(self):
        """Empty input list should immediately return empty list without HTTP requests."""
        result = await EmbeddingClient.generate_batch_embeddings([])
        self.assertEqual(result, [])

    async def test_generate_batch_embeddings_success_ordering(self):
        """Batch embedding should preserve 1:1 input ordering."""
        texts = ["Text chunk 1", "Text chunk 2", "Text chunk 3"]
        mock_response = MagicMock()
        mock_response.json.return_value = {
            "model": "bge-m3",
            "embeddings": [
                [0.1, 0.2, 0.3],
                [0.4, 0.5, 0.6],
                [0.7, 0.8, 0.9]
            ],
            "dimension": 3
        }
        mock_response.raise_for_status = MagicMock()

        mock_client = AsyncMock()
        mock_client.post = AsyncMock(return_value=mock_response)

        result = await EmbeddingClient.generate_batch_embeddings(texts, batch_size=50, client=mock_client)

        self.assertEqual(len(result), 3)
        self.assertEqual(result[0], [0.1, 0.2, 0.3])
        self.assertEqual(result[1], [0.4, 0.5, 0.6])
        self.assertEqual(result[2], [0.7, 0.8, 0.9])

        # Verify only one batch HTTP request was sent with all texts, auth headers, and 60.0s timeout
        mock_client.post.assert_called_once_with(
            "http://localhost:8001/api/v1/embeddings/batch",
            json={"texts": texts},
            headers={
                "X-Internal-Service-Secret": "test-rag-secret",
                "X-Internal-Caller-Service": "rag-service"
            },
            timeout=60.0
        )

    async def test_generate_batch_embeddings_bounded_batching(self):
        """Large text arrays should be split into bounded sub-batches."""
        # 12 items with batch_size=5 -> 3 sub-batches (5, 5, 2)
        texts = [f"Text chunk {i}" for i in range(12)]

        def make_response(sub_texts):
            resp = MagicMock()
            resp.json.return_value = {
                "embeddings": [[float(i), float(i), float(i)] for i in range(len(sub_texts))],
                "dimension": 3
            }
            resp.raise_for_status = MagicMock()
            return resp

        mock_client = AsyncMock()
        mock_client.post = AsyncMock(side_effect=[
            make_response(texts[:5]),
            make_response(texts[5:10]),
            make_response(texts[10:])
        ])

        result = await EmbeddingClient.generate_batch_embeddings(texts, batch_size=5, client=mock_client)

        self.assertEqual(len(result), 12)
        self.assertEqual(mock_client.post.call_count, 3)

    async def test_generate_batch_embeddings_mismatched_count(self):
        """Should raise ValueError if returned embedding count does not match input count."""
        texts = ["Chunk 1", "Chunk 2"]
        mock_response = MagicMock()
        mock_response.json.return_value = {
            "embeddings": [[0.1, 0.2, 0.3]],  # Only 1 embedding returned for 2 texts
            "dimension": 3
        }
        mock_response.raise_for_status = MagicMock()

        mock_client = AsyncMock()
        mock_client.post = AsyncMock(return_value=mock_response)

        with self.assertRaises(ValueError) as ctx:
            await EmbeddingClient.generate_batch_embeddings(texts, batch_size=50, client=mock_client)

        self.assertIn("Mismatched embedding count", str(ctx.exception))

    async def test_generate_batch_embeddings_invalid_dimension(self):
        """Should raise ValueError if returned embedding vector has unexpected dimensions."""
        texts = ["Chunk 1"]
        mock_response = MagicMock()
        mock_response.json.return_value = {
            "embeddings": [[0.1, 0.2]],  # Dimension 2 instead of expected 3
            "dimension": 2
        }
        mock_response.raise_for_status = MagicMock()

        mock_client = AsyncMock()
        mock_client.post = AsyncMock(return_value=mock_response)

        with self.assertRaises(ValueError) as ctx:
            await EmbeddingClient.generate_batch_embeddings(texts, batch_size=50, client=mock_client)

        self.assertIn("Invalid embedding dimension", str(ctx.exception))

    async def test_generate_batch_embeddings_malformed_response(self):
        """Should raise ValueError if response is missing embeddings field."""
        texts = ["Chunk 1"]
        mock_response = MagicMock()
        mock_response.json.return_value = {"status": "ok"}  # Missing embeddings field
        mock_response.raise_for_status = MagicMock()

        mock_client = AsyncMock()
        mock_client.post = AsyncMock(return_value=mock_response)

        with self.assertRaises(ValueError) as ctx:
            await EmbeddingClient.generate_batch_embeddings(texts, batch_size=50, client=mock_client)

        self.assertIn("Malformed batch embedding response", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
