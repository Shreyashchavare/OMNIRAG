"""
Unit and integration tests for Embedding Service startup hardening & fail-closed configuration (Stage 3.6 Batch 4.1).
Verifies:
1. Valid configured secret permits startup and initializes resources.
2. Missing secret (None) causes startup failure with sanitized exception.
3. Empty secret ("") causes startup failure.
4. Whitespace-only secret ("   ") causes startup failure.
5. Invalid configuration fails before creating HTTP clients or background resources.
6. Valid startup still initializes existing resources (app.state.http_client).
7. Shutdown still closes existing resources.
8. Direct settings.validate_startup_configuration() unit verification.
"""

import os
import sys
import unittest
from unittest.mock import patch

import httpx
from fastapi import FastAPI
from fastapi.testclient import TestClient

embedding_service_path = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if embedding_service_path not in sys.path:
    sys.path.insert(0, embedding_service_path)

from app.config.settings import settings
from app.main import app, lifespan


class TestEmbeddingStartupHardening(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.orig_secret = getattr(settings, "INTERNAL_SERVICE_SECRET", None)
        self.valid_secret = "test-cluster-service-secret-777"
        settings.INTERNAL_SERVICE_SECRET = self.valid_secret

    def tearDown(self):
        settings.INTERNAL_SERVICE_SECRET = self.orig_secret
        if hasattr(app.state, "http_client"):
            delattr(app.state, "http_client")

    def test_direct_validation_with_valid_secret(self):
        """validate_startup_configuration succeeds when INTERNAL_SERVICE_SECRET is non-empty."""
        settings.INTERNAL_SERVICE_SECRET = "valid-secret-key"
        # Must not raise
        settings.validate_startup_configuration()

    def test_direct_validation_with_none_secret_raises(self):
        """validate_startup_configuration raises RuntimeError when secret is None."""
        settings.INTERNAL_SERVICE_SECRET = None
        with self.assertRaises(RuntimeError) as ctx:
            settings.validate_startup_configuration()
        self.assertIn("INTERNAL_SERVICE_SECRET", str(ctx.exception))
        self.assertNotIn("None", str(ctx.exception))

    def test_direct_validation_with_empty_secret_raises(self):
        """validate_startup_configuration raises RuntimeError when secret is empty."""
        settings.INTERNAL_SERVICE_SECRET = ""
        with self.assertRaises(RuntimeError) as ctx:
            settings.validate_startup_configuration()
        self.assertIn("INTERNAL_SERVICE_SECRET", str(ctx.exception))

    def test_direct_validation_with_whitespace_secret_raises(self):
        """validate_startup_configuration raises RuntimeError for whitespace-only strings."""
        for ws in ["   ", "\t", "\n", " \t \r\n "]:
            with self.subTest(whitespace=repr(ws)):
                settings.INTERNAL_SERVICE_SECRET = ws
                with self.assertRaises(RuntimeError) as ctx:
                    settings.validate_startup_configuration()
                self.assertIn("INTERNAL_SERVICE_SECRET", str(ctx.exception))

    async def test_valid_configured_secret_permits_startup(self):
        """Valid secret allows application startup and initializes app.state.http_client."""
        settings.INTERNAL_SERVICE_SECRET = self.valid_secret
        test_app = FastAPI(lifespan=lifespan)
        async with lifespan(test_app):
            self.assertTrue(hasattr(test_app.state, "http_client"))
            client = test_app.state.http_client
            self.assertIsInstance(client, httpx.AsyncClient)
            self.assertFalse(client.is_closed)

        # After shutdown, client must be closed
        self.assertTrue(client.is_closed)

    async def test_missing_secret_fails_startup_before_resource_allocation(self):
        """Missing secret fails startup before creating any httpx.AsyncClient."""
        settings.INTERNAL_SERVICE_SECRET = None
        test_app = FastAPI(lifespan=lifespan)

        with patch("httpx.AsyncClient") as mock_client:
            with self.assertRaises(RuntimeError) as ctx:
                async with lifespan(test_app):
                    pass

            self.assertIn("INTERNAL_SERVICE_SECRET", str(ctx.exception))
            # Verify HTTP client was never created
            mock_client.assert_not_called()
            self.assertFalse(hasattr(test_app.state, "http_client"))

    async def test_empty_secret_fails_startup_before_resource_allocation(self):
        """Empty secret fails startup before creating any httpx.AsyncClient."""
        settings.INTERNAL_SERVICE_SECRET = ""
        test_app = FastAPI(lifespan=lifespan)

        with patch("httpx.AsyncClient") as mock_client:
            with self.assertRaises(RuntimeError) as ctx:
                async with lifespan(test_app):
                    pass

            self.assertIn("INTERNAL_SERVICE_SECRET", str(ctx.exception))
            mock_client.assert_not_called()
            self.assertFalse(hasattr(test_app.state, "http_client"))

    async def test_whitespace_secret_fails_startup_before_resource_allocation(self):
        """Whitespace secret fails startup before creating any httpx.AsyncClient."""
        settings.INTERNAL_SERVICE_SECRET = "   \t   "
        test_app = FastAPI(lifespan=lifespan)

        with patch("httpx.AsyncClient") as mock_client:
            with self.assertRaises(RuntimeError) as ctx:
                async with lifespan(test_app):
                    pass

            self.assertIn("INTERNAL_SERVICE_SECRET", str(ctx.exception))
            mock_client.assert_not_called()
            self.assertFalse(hasattr(test_app.state, "http_client"))

    def test_testclient_startup_fails_closed_when_secret_empty(self):
        """FastAPI TestClient fails during startup if secret is empty."""
        settings.INTERNAL_SERVICE_SECRET = ""
        with self.assertRaises(RuntimeError) as ctx:
            with TestClient(app):
                pass
        self.assertIn("INTERNAL_SERVICE_SECRET", str(ctx.exception))

    def test_testclient_startup_succeeds_when_secret_valid(self):
        """FastAPI TestClient starts successfully when secret is valid."""
        settings.INTERNAL_SERVICE_SECRET = self.valid_secret
        with TestClient(app) as client:
            with patch("app.main.embedding_service.check_ollama", return_value=True):
                resp = client.get("/health")
                self.assertEqual(resp.status_code, 200)


if __name__ == "__main__":
    unittest.main()
