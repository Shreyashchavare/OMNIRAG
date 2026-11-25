"""
Unit tests for Agent service:
- AgentRequest schema requiring knowledge_base_id
- Header propagation to RAG service
- Context construction scoped strictly to the target Knowledge Base
"""

import os
import sys
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

agent_service_path = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if agent_service_path not in sys.path:
    sys.path.insert(0, agent_service_path)

# Mock langchain_ollama if not installed
if "langchain_ollama" not in sys.modules:
    mock_llm_mod = MagicMock()
    sys.modules["langchain_ollama"] = mock_llm_mod

# Mock httpx if not installed
if "httpx" not in sys.modules:
    mock_httpx = MagicMock()
    mock_httpx.HTTPError = Exception
    mock_httpx.HTTPStatusError = Exception
    sys.modules["httpx"] = mock_httpx

from app.models.schemas import AgentRequest, AgentResponse
from app.services.agent_service import AgentService


class TestAgentService(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.service = AgentService()
        self.service.llm = AsyncMock()
        mock_response = MagicMock()
        mock_response.content = "Answer based on context"
        self.service.llm.ainvoke = AsyncMock(return_value=mock_response)

    def test_agent_request_validation(self):
        """AgentRequest requires non-empty query and knowledge_base_id."""
        req = AgentRequest(query="What is OMRAGUL?", knowledge_base_id="kb-123")
        self.assertEqual(req.query, "What is OMRAGUL?")
        self.assertEqual(req.knowledge_base_id, "kb-123")

        # Missing query or knowledge_base_id should fail validation
        with self.assertRaises(Exception):
            AgentRequest(query="", knowledge_base_id="kb-123")

        with self.assertRaises(Exception):
            AgentRequest(query="Valid query", knowledge_base_id="")

    async def test_search_rag_propagates_kb_and_headers(self):
        """search_rag must pass knowledge_base_id and forwarding headers to RAG service."""
        mock_response = MagicMock()
        mock_response.json.return_value = {
            "results": [
                {"score": 0.95, "document": {"text": "OMRAGUL is offline RAG", "document_id": "doc-1"}}
            ]
        }
        mock_response.raise_for_status = MagicMock()

        mock_client = AsyncMock()
        mock_client.post = AsyncMock(return_value=mock_response)

        with patch("httpx.AsyncClient") as mock_client_cls:
            mock_client_cls.return_value.__aenter__.return_value = mock_client

            headers = {
                "X-Gateway-Assertion": "assertion-token-xyz",
                "X-User-Id": "user-uuid-1"
            }
            res = await self.service.search_rag(
                query="What is OMRAGUL?",
                knowledge_base_id="kb-456",
                headers=headers
            )

            self.assertEqual(len(res["results"]), 1)
            mock_client.post.assert_called_once()
            call_args, call_kwargs = mock_client.post.call_args
            self.assertEqual(call_kwargs["json"]["query"], "What is OMRAGUL?")
            self.assertEqual(call_kwargs["json"]["knowledge_base_id"], "kb-456")
            self.assertEqual(call_kwargs["headers"]["X-Gateway-Assertion"], "assertion-token-xyz")
            self.assertEqual(call_kwargs["headers"]["X-User-Id"], "user-uuid-1")

    async def test_chat_context_construction(self):
        """Chat prompt must only contain text from the scoped Knowledge Base search."""
        mock_rag_result = {
            "results": [
                {"score": 0.9, "document": {"text": "Scoped chunk A", "document_id": "doc-1"}},
                {"score": 0.8, "document": {"text": "Scoped chunk B", "document_id": "doc-2"}}
            ]
        }
        self.service.search_rag = AsyncMock(return_value=mock_rag_result)

        answer, sources = await self.service.chat(
            query="Tell me about project X",
            knowledge_base_id="kb-789",
            headers={"X-User-Id": "user-1"}
        )

        self.assertEqual(answer, "Answer based on context")
        self.assertEqual(len(sources), 2)
        self.service.search_rag.assert_called_once_with(
            "Tell me about project X",
            "kb-789",
            {"X-User-Id": "user-1"}
        )

        # Verify LLM prompt contains the scoped chunks
        call_prompt = self.service.llm.ainvoke.call_args[0][0]
        self.assertIn("Scoped chunk A", call_prompt)
        self.assertIn("Scoped chunk B", call_prompt)


if __name__ == "__main__":
    unittest.main()
