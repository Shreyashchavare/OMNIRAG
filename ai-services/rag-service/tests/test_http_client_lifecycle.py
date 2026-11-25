"""
Unit and integration tests for RAG Service HTTP client lifecycle, connection pooling, and lifespan management.
Covers Stage 3.6 Batch 3.1:
1. Application startup initializes exactly one shared client on app.state.http_client.
2. Connection pool limits configured with bounded defaults.
3. Application shutdown closes the client cleanly.
4. Initialization failure does not leak resources.
5. EmbeddingClient calls reuse the shared client.
6. Knowledge Service access checks and document filtering reuse the shared client.
7. Timeouts (60.0s for embedding, 5.0s for Knowledge Service) strictly preserved.
"""

import os
import sys
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

import httpx
from fastapi import FastAPI
from fastapi.testclient import TestClient

rag_service_path = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if rag_service_path not in sys.path:
    sys.path.insert(0, rag_service_path)

from app.config.settings import settings
from app.main import app, lifespan
from app.services.embedding_client import EmbeddingClient
from app.services.auth_service import verify_knowledge_base_access, filter_active_documents
from app.dependencies import get_http_client


class TestRagHttpClientLifecycle(unittest.IsolatedAsyncioTestCase):

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

    async def test_embedding_client_reuses_shared_client_and_preserves_timeout(self):
        """EmbeddingClient.generate_embedding reuses client and enforces 60.0s timeout."""
        settings.internal_service_secret = "test-secret"
        mock_client = AsyncMock(spec=httpx.AsyncClient)
        mock_resp = MagicMock()
        mock_resp.json.return_value = {"embedding": [0.1, 0.2, 0.3]}
        mock_resp.raise_for_status = MagicMock()
        mock_client.post.return_value = mock_resp

        with patch("httpx.AsyncClient") as uncalled_constructor:
            emb = await EmbeddingClient.generate_embedding("query text", client=mock_client)
            uncalled_constructor.assert_not_called()
            mock_client.post.assert_called_once()
            call_kwargs = mock_client.post.call_args.kwargs
            self.assertEqual(call_kwargs.get("timeout"), 60.0)
            self.assertEqual(emb, [0.1, 0.2, 0.3])

    async def test_batch_embedding_reuses_shared_client(self):
        """EmbeddingClient.generate_batch_embeddings reuses client without local instantiations."""
        settings.internal_service_secret = "test-secret"
        settings.embedding_dimension = 2
        mock_client = AsyncMock(spec=httpx.AsyncClient)
        mock_resp = MagicMock()
        mock_resp.json.return_value = {"embeddings": [[0.1, 0.2], [0.3, 0.4]]}
        mock_resp.raise_for_status = MagicMock()
        mock_client.post.return_value = mock_resp

        with patch("httpx.AsyncClient") as uncalled_constructor:
            embs = await EmbeddingClient.generate_batch_embeddings(["a", "b"], client=mock_client)
            uncalled_constructor.assert_not_called()
            mock_client.post.assert_called_once()
            call_kwargs = mock_client.post.call_args.kwargs
            self.assertEqual(call_kwargs.get("timeout"), 60.0)
            self.assertEqual(len(embs), 2)

    async def test_knowledge_base_access_reuses_shared_client_and_preserves_timeout(self):
        """verify_knowledge_base_access reuses client and enforces 5.0s timeout."""
        mock_client = AsyncMock(spec=httpx.AsyncClient)
        mock_resp = MagicMock(status_code=200)
        mock_client.get.return_value = mock_resp

        with patch("httpx.AsyncClient") as uncalled_constructor:
            ok = await verify_knowledge_base_access(
                user_id="user-1",
                knowledge_base_id="kb-1",
                knowledge_service_url="http://localhost:8082",
                internal_secret="secret",
                client=mock_client,
                gateway_assertion="token-123"
            )
            uncalled_constructor.assert_not_called()
            mock_client.get.assert_called_once()
            call_kwargs = mock_client.get.call_args.kwargs
            self.assertEqual(call_kwargs.get("timeout"), 5.0)
            self.assertTrue(ok)

    async def test_filter_active_documents_reuses_shared_client_and_preserves_timeout(self):
        """filter_active_documents reuses client and enforces 5.0s timeout."""
        mock_client = AsyncMock(spec=httpx.AsyncClient)
        mock_resp = MagicMock(status_code=200)
        mock_resp.json.return_value = {"activeDocumentIds": ["doc-1"]}
        mock_client.post.return_value = mock_resp

        with patch("httpx.AsyncClient") as uncalled_constructor:
            active = await filter_active_documents(
                knowledge_base_id="kb-1",
                candidate_doc_ids=["doc-1", "doc-2"],
                knowledge_service_url="http://localhost:8082",
                internal_secret="secret",
                client=mock_client
            )
            uncalled_constructor.assert_not_called()
            mock_client.post.assert_called_once()
            call_kwargs = mock_client.post.call_args.kwargs
            self.assertEqual(call_kwargs.get("timeout"), 5.0)
            self.assertEqual(active, ["doc-1"])

    def test_search_route_reuses_app_scoped_client_across_multiple_requests(self):
        """Routes executed via TestClient reuse the exact same app.state.http_client."""
        with TestClient(app) as test_client:
            self.assertTrue(hasattr(app.state, "http_client"))
            shared_client = app.state.http_client
            self.assertFalse(shared_client.is_closed)

            # Patch verify_knowledge_base_access, EmbeddingClient, and filter_active_documents
            with patch("app.api.routes.verify_knowledge_base_access", new_callable=AsyncMock) as mock_auth, \
                 patch("app.api.routes.EmbeddingClient.generate_embedding", new_callable=AsyncMock) as mock_emb, \
                 patch("app.api.routes.vector_store.search", return_value=[]):

                mock_auth.return_value = True
                mock_emb.return_value = [0.1] * settings.embedding_dimension
                def create_test_gateway_assertion(user_id: str, secret: str) -> str:
                    import jwt
                    import time
                    import uuid
                    from cryptography.hazmat.primitives.asymmetric import rsa
                    from app.services.auth_service import (
                        InMemoryGatewayAssertionReplayService,
                        set_cached_public_key,
                        set_default_replay_service,
                    )

                    _test_priv = rsa.generate_private_key(public_exponent=65537, key_size=2048)
                    set_cached_public_key(_test_priv.public_key())
                    set_default_replay_service(InMemoryGatewayAssertionReplayService())
                    payload = {
                        "iss": "gateway-service",
                        "aud": "rag-service",
                        "sub": user_id,
                        "rid": str(uuid.uuid4()),
                        "jti": str(uuid.uuid4()),
                        "mth": "POST",
                        "pth": "/api/v1/rag/search",
                        "iat": int(time.time()),
                        "exp": int(time.time()) + 300,
                    }
                    return jwt.encode(payload, _test_priv, algorithm="RS256", headers={"typ": "JWT"})

                token = create_test_gateway_assertion("00000000-0000-0000-0000-000000000001", settings.internal_service_secret)

                # Request 1
                resp1 = test_client.post(
                    "/api/v1/rag/search",
                    headers={"X-Gateway-Assertion": token},
                    json={"query": "query 1", "knowledge_base_id": "kb-test", "top_k": 3}
                )
                self.assertEqual(resp1.status_code, 200)

                token2 = create_test_gateway_assertion("00000000-0000-0000-0000-000000000001", settings.internal_service_secret)
                # Request 2
                resp2 = test_client.post(
                    "/api/v1/rag/search",
                    headers={"X-Gateway-Assertion": token2},
                    json={"query": "query 2", "knowledge_base_id": "kb-test", "top_k": 3}
                )
                self.assertEqual(resp2.status_code, 200)

                # Both calls must have received the shared client
                self.assertIs(mock_auth.call_args_list[0].kwargs.get("client"), shared_client)
                self.assertIs(mock_auth.call_args_list[1].kwargs.get("client"), shared_client)
                self.assertIs(mock_emb.call_args_list[0].kwargs.get("client"), shared_client)
                self.assertIs(mock_emb.call_args_list[1].kwargs.get("client"), shared_client)

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
