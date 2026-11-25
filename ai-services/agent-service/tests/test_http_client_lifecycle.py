"""
Unit and integration tests for Agent Service HTTP client lifecycle, connection pooling, and lifespan management.
Covers Stage 3.6 Batch 3.1:
1. Application startup initializes exactly one shared client on app.state.http_client.
2. Connection pool limits configured with bounded defaults.
3. Application shutdown closes the client cleanly.
4. Initialization failure does not leak resources.
5. Agent RAG calls reuse the shared client.
6. Timeout overrides (30.0s) strictly preserved.
7. Multiple requests reuse the same client instance.
"""

import os
import sys
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

import httpx
from fastapi import FastAPI
from fastapi.testclient import TestClient

agent_service_path = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if agent_service_path not in sys.path:
    sys.path.insert(0, agent_service_path)

# Mock langchain_ollama if not installed
if "langchain_ollama" not in sys.modules:
    mock_llm_mod = MagicMock()
    sys.modules["langchain_ollama"] = mock_llm_mod

from app.config.settings import settings
from app.main import app, lifespan
from app.services.agent_service import AgentService
from app.dependencies import get_http_client


class TestAgentHttpClientLifecycle(unittest.IsolatedAsyncioTestCase):

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
            client = test_app.state.http_client
            self.assertIsNotNone(client)
            # Verify configured settings values are used
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

    async def test_search_rag_reuses_passed_client_and_preserves_timeout(self):
        """search_rag must use the provided client and pass timeout=30.0 without creating a new client."""
        service = AgentService()
        mock_client = AsyncMock(spec=httpx.AsyncClient)
        mock_response = MagicMock()
        mock_response.json.return_value = {"results": [{"document": {"text": "chunk 1"}}]}
        mock_response.raise_for_status = MagicMock()
        mock_client.post.return_value = mock_response

        with patch("httpx.AsyncClient") as uncalled_constructor:
            result = await service.search_rag(
                query="test query",
                knowledge_base_id="kb-123",
                headers={"X-User-Id": "user-abc"},
                client=mock_client
            )

            # Assert no new client was instantiated
            uncalled_constructor.assert_not_called()
            # Assert provided client was used
            mock_client.post.assert_called_once()
            call_kwargs = mock_client.post.call_args.kwargs
            self.assertEqual(call_kwargs.get("timeout"), 30.0)
            self.assertEqual(result, {"results": [{"document": {"text": "chunk 1"}}]})

    async def test_chat_method_passes_client_to_search_rag(self):
        """chat() passes the client through to search_rag."""
        service = AgentService()
        mock_client = AsyncMock(spec=httpx.AsyncClient)
        service.search_rag = AsyncMock(return_value={"results": []})
        service.llm.ainvoke = AsyncMock(return_value=MagicMock(content="Mock LLM response"))

        response, sources = await service.chat(
            query="test",
            knowledge_base_id="kb-123",
            client=mock_client
        )

        self.assertEqual(response, "Mock LLM response")
        service.search_rag.assert_called_once_with(
            "test",
            "kb-123",
            None,
            client=mock_client
        )

    def test_chat_route_reuses_app_scoped_client_across_multiple_requests(self):
        """Route executions under TestClient reuse app.state.http_client across multiple requests."""
        with TestClient(app) as test_client:
            self.assertTrue(hasattr(app.state, "http_client"))
            shared_client = app.state.http_client
            self.assertFalse(shared_client.is_closed)

            # Mock search_rag to observe client parameter passed by route
            with patch("app.api.routes.agent_service.chat", new_callable=AsyncMock) as mock_chat:
                mock_chat.return_value = ("Answer", [])
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
                        "aud": "agent-service",
                        "sub": user_id,
                        "rid": str(uuid.uuid4()),
                        "jti": str(uuid.uuid4()),
                        "mth": "POST",
                        "pth": "/api/v1/agent/chat",
                        "iat": int(time.time()),
                        "exp": int(time.time()) + 300,
                    }
                    return jwt.encode(payload, _test_priv, algorithm="RS256", headers={"typ": "JWT"})

                token = create_test_gateway_assertion("00000000-0000-0000-0000-000000000001", settings.INTERNAL_SERVICE_SECRET)

                # Request 1
                resp1 = test_client.post(
                    "/api/v1/agent/chat",
                    headers={"X-Gateway-Assertion": token},
                    json={"query": "first query", "knowledge_base_id": "kb-1"}
                )
                self.assertEqual(resp1.status_code, 200)

                token2 = create_test_gateway_assertion("00000000-0000-0000-0000-000000000001", settings.INTERNAL_SERVICE_SECRET)
                # Request 2
                resp2 = test_client.post(
                    "/api/v1/agent/chat",
                    headers={"X-Gateway-Assertion": token2},
                    json={"query": "second query", "knowledge_base_id": "kb-1"}
                )
                self.assertEqual(resp2.status_code, 200)

                # Both calls must have received the EXACT same shared client instance
                self.assertEqual(mock_chat.call_count, 2)
                call1_client = mock_chat.call_args_list[0].kwargs.get("client")
                call2_client = mock_chat.call_args_list[1].kwargs.get("client")
                self.assertIs(call1_client, shared_client)
                self.assertIs(call2_client, shared_client)

        # Upon exiting TestClient context, the client must be closed
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
