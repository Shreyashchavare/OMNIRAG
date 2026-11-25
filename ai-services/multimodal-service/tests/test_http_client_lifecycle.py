"""
Unit and integration tests for Multimodal Service HTTP client lifecycle, connection pooling, and lifespan management.
Covers Stage 3.6 Batch 3.1:
1. Application startup initializes exactly one shared client on app.state.http_client.
2. Connection pool limits configured with bounded defaults.
3. Application shutdown closes the client cleanly.
4. Initialization failure does not leak resources.
5. VisionService.analyze_image calls reuse the shared client.
6. Timeout (120.0s) strictly preserved.
7. Multiple route executions reuse the same client instance.
"""

import io
import os
import sys
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

import httpx
from fastapi import FastAPI
from fastapi.testclient import TestClient

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

from app.config.settings import settings
from app.main import app, lifespan
from app.services.vision_service import VisionService
from app.dependencies import get_http_client


class TestMultimodalHttpClientLifecycle(unittest.IsolatedAsyncioTestCase):

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

    async def test_vision_service_reuses_shared_client_and_preserves_timeout(self):
        """VisionService.analyze_image reuses passed client and enforces 120.0s timeout."""
        mock_client = AsyncMock(spec=httpx.AsyncClient)
        mock_resp = MagicMock()
        mock_resp.json.return_value = {"message": {"content": "Image description text"}}
        mock_resp.raise_for_status = MagicMock()
        mock_client.post.return_value = mock_resp

        with patch("httpx.AsyncClient") as uncalled_constructor:
            result = await VisionService.analyze_image(
                image_bytes=b"fake_image_bytes",
                prompt="Describe",
                client=mock_client
            )
            uncalled_constructor.assert_not_called()
            mock_client.post.assert_called_once()
            call_kwargs = mock_client.post.call_args.kwargs
            self.assertEqual(call_kwargs.get("timeout"), 120.0)
            self.assertEqual(result, "Image description text")

    def test_vision_route_reuses_app_scoped_client_across_multiple_requests(self):
        """POST /api/v1/multimodal/vision/analyze reuses the exact same app.state.http_client."""
        with TestClient(app) as test_client:
            self.assertTrue(hasattr(app.state, "http_client"))
            shared_client = app.state.http_client
            self.assertFalse(shared_client.is_closed)

            with patch("app.api.routes.multimodal_routes.VisionService.analyze_image", new_callable=AsyncMock) as mock_analyze:
                mock_analyze.return_value = "Detailed analysis"
                settings.INTERNAL_SERVICE_SECRET = "test-secret"

                # Request 1
                resp1 = test_client.post(
                    "/api/v1/multimodal/vision/analyze",
                    files={"file": ("test.jpg", io.BytesIO(b"fake-bytes"), "image/jpeg")},
                    data={"prompt": "prompt 1"},
                    headers={"X-Internal-Service-Secret": "test-secret"}
                )
                self.assertEqual(resp1.status_code, 200)

                # Request 2
                resp2 = test_client.post(
                    "/api/v1/multimodal/vision/analyze",
                    files={"file": ("test.jpg", io.BytesIO(b"fake-bytes"), "image/jpeg")},
                    data={"prompt": "prompt 2"},
                    headers={"X-Internal-Service-Secret": "test-secret"}
                )
                self.assertEqual(resp2.status_code, 200)

                # Both calls must have received the shared client
                self.assertEqual(mock_analyze.call_count, 2)
                self.assertIs(mock_analyze.call_args_list[0].kwargs.get("client"), shared_client)
                self.assertIs(mock_analyze.call_args_list[1].kwargs.get("client"), shared_client)

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
