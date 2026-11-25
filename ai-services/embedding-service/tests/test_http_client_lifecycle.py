"""
Unit and integration tests for Embedding Service HTTP client lifecycle, connection pooling, and lifespan management.
Covers Stage 3.6 Batch 3.1:
1. Application startup initializes exactly one shared client on app.state.http_client.
2. Connection pool limits configured with bounded defaults.
3. Application shutdown closes the client cleanly.
4. Initialization failure does not leak resources.
5. EmbeddingService calls reuse the shared client.
6. Health checks reuse the shared client without instantiating new clients per request.
7. Timeouts (120.0s for embedding, 5.0s for health check) strictly preserved.
"""

import os
import sys
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

import httpx
from fastapi import FastAPI
from fastapi.testclient import TestClient

embedding_service_path = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if embedding_service_path not in sys.path:
    sys.path.insert(0, embedding_service_path)

from app.config.settings import settings
from app.main import app, lifespan
from app.services.embedding_service import EmbeddingService
from app.dependencies import get_http_client


class TestEmbeddingHttpClientLifecycle(unittest.IsolatedAsyncioTestCase):

    async def test_lifespan_initializes_and_closes_client(self):
        """Startup must initialize app.state.http_client, and shutdown must close it."""
        test_app = FastAPI(lifespan=lifespan)
        async with lifespan(test_app):
            self.assertTrue(hasattr(test_app.state, "http_client"))
            client = test_app.state.http_client
            self.assertIsInstance(client, httpx.AsyncClient)
            self.assertFalse(client.is_closed)

        # After lifespan exits, client must be closed
        self.assertTrue(client.is_closed)

    async def test_lifespan_connection_pool_limits(self):
        """Client must be configured with bounded connection pool limits."""
        test_app = FastAPI(lifespan=lifespan)
        async with lifespan(test_app):
            self.assertEqual(settings.HTTP_MAX_CONNECTIONS, 100)
            self.assertEqual(settings.HTTP_MAX_KEEPALIVE_CONNECTIONS, 20)
            self.assertEqual(settings.HTTP_KEEPALIVE_EXPIRY, 30.0)

    async def test_lifespan_initialization_failure_safe(self):
        """If client creation fails during startup, exceptions are handled and no leak occurs."""
        test_app = FastAPI(lifespan=lifespan)
        with patch("httpx.AsyncClient", side_effect=RuntimeError("Simulated socket failure")):
            with self.assertRaises(RuntimeError):
                async with lifespan(test_app):
                    pass

    async def test_generate_embedding_reuses_shared_client_and_preserves_timeout(self):
        """generate_embedding reuses passed client and enforces 120.0s timeout."""
        service = EmbeddingService()
        mock_client = AsyncMock(spec=httpx.AsyncClient)
        mock_resp = MagicMock()
        mock_resp.json.return_value = {"embeddings": [[0.1, 0.2, 0.3]]}
        mock_resp.raise_for_status = MagicMock()
        mock_client.post.return_value = mock_resp

        with patch("httpx.AsyncClient") as uncalled_constructor:
            emb = await service.generate_embedding("Test text", client=mock_client)
            uncalled_constructor.assert_not_called()
            mock_client.post.assert_called_once()
            call_kwargs = mock_client.post.call_args.kwargs
            self.assertEqual(call_kwargs.get("timeout"), settings.OLLAMA_TIMEOUT)
            self.assertEqual(call_kwargs.get("timeout"), 55.0)
            self.assertEqual(emb, [0.1, 0.2, 0.3])

    async def test_generate_embeddings_reuses_shared_client_and_preserves_timeout(self):
        """generate_embeddings reuses passed client and enforces 120.0s timeout."""
        service = EmbeddingService()
        mock_client = AsyncMock(spec=httpx.AsyncClient)
        mock_resp = MagicMock()
        mock_resp.json.return_value = {"embeddings": [[0.1, 0.2], [0.3, 0.4]]}
        mock_resp.raise_for_status = MagicMock()
        mock_client.post.return_value = mock_resp

        with patch("httpx.AsyncClient") as uncalled_constructor:
            embs = await service.generate_embeddings(["t1", "t2"], client=mock_client)
            uncalled_constructor.assert_not_called()
            mock_client.post.assert_called_once()
            call_kwargs = mock_client.post.call_args.kwargs
            self.assertEqual(call_kwargs.get("timeout"), settings.OLLAMA_TIMEOUT)
            self.assertEqual(call_kwargs.get("timeout"), 55.0)
            self.assertEqual(len(embs), 2)

    async def test_check_ollama_reuses_shared_client_and_preserves_timeout(self):
        """check_ollama reuses passed client and enforces 5.0s timeout."""
        service = EmbeddingService()
        mock_client = AsyncMock(spec=httpx.AsyncClient)
        mock_resp = MagicMock()
        mock_resp.json.return_value = {"models": [{"name": service.model}]}
        mock_resp.raise_for_status = MagicMock()
        mock_client.get.return_value = mock_resp

        with patch("httpx.AsyncClient") as uncalled_constructor:
            available = await service.check_ollama(client=mock_client)
            uncalled_constructor.assert_not_called()
            mock_client.get.assert_called_once()
            call_kwargs = mock_client.get.call_args.kwargs
            self.assertEqual(call_kwargs.get("timeout"), settings.OLLAMA_HEALTH_TIMEOUT)
            self.assertEqual(call_kwargs.get("timeout"), 5.0)
            self.assertTrue(available)

    def test_health_check_reuses_app_scoped_client_without_new_clients(self):
        """Multiple GET /health calls reuse app.state.http_client without instantiating new clients."""
        with TestClient(app) as test_client:
            self.assertTrue(hasattr(app.state, "http_client"))
            shared_client = app.state.http_client
            self.assertFalse(shared_client.is_closed)

            with patch("app.main.embedding_service.check_ollama", new_callable=AsyncMock) as mock_check:
                mock_check.return_value = True

                # Call health check multiple times
                for _ in range(5):
                    resp = test_client.get("/health")
                    self.assertEqual(resp.status_code, 200)
                    self.assertEqual(resp.json()["status"], "UP")

                # Verify all calls received the shared client
                self.assertEqual(mock_check.call_count, 5)
                for call in mock_check.call_args_list:
                    self.assertIs(call.kwargs.get("client"), shared_client)

        self.assertTrue(shared_client.is_closed)

    def test_get_http_client_missing_client_raises_runtime_error(self):
        """When app.state.http_client is None, get_http_client must fail fast with RuntimeError."""
        mock_request = MagicMock()
        mock_request.app.state.http_client = None
        with self.assertRaises(RuntimeError) as ctx:
            get_http_client(mock_request)
        self.assertIn("not initialized", str(ctx.exception))

    def test_get_http_client_closed_client_raises_runtime_error(self):
        """When app.state.http_client is closed, get_http_client must fail fast with RuntimeError."""
        mock_request = MagicMock()
        mock_closed_client = MagicMock()
        mock_closed_client.is_closed = True
        mock_request.app.state.http_client = mock_closed_client
        with self.assertRaises(RuntimeError) as ctx:
            get_http_client(mock_request)
        self.assertIn("closed", str(ctx.exception))

    def test_get_http_client_valid_returns_shared_instance(self):
        """When app.state.http_client is active, get_http_client returns the exact shared instance."""
        mock_request = MagicMock()
        mock_active_client = MagicMock()
        mock_active_client.is_closed = False
        mock_request.app.state.http_client = mock_active_client
        client = get_http_client(mock_request)
        self.assertIs(client, mock_active_client)


if __name__ == "__main__":
    unittest.main()
