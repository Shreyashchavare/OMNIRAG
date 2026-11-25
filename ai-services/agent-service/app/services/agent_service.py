import logging
from typing import Any, Dict, List, Optional, Tuple
import httpx
from langchain_ollama import ChatOllama

from app.config.settings import settings

logger = logging.getLogger(__name__)


class AgentService:

    def __init__(self):
        self.llm = ChatOllama(
            base_url=settings.OLLAMA_BASE_URL,
            model=settings.OLLAMA_MODEL
        )

    async def search_rag(
        self,
        query: str,
        knowledge_base_id: str,
        headers: Optional[Dict[str, str]] = None,
        client: Optional[httpx.AsyncClient] = None
    ) -> Dict[str, Any]:
        """
        Queries RAG service scoped strictly to knowledge_base_id.
        Propagates verified caller identity headers for authorization.
        Reuses application-scoped http_client when provided.
        """
        payload = {
            "query": query,
            "knowledge_base_id": knowledge_base_id
        }

        async def _do_search(http_client: httpx.AsyncClient) -> Dict[str, Any]:
            response = await http_client.post(
                f"{settings.RAG_SERVICE_URL}/api/v1/rag/search",
                json=payload,
                headers=headers or {},
                timeout=30.0
            )
            response.raise_for_status()
            return response.json()

        if client is not None:
            return await _do_search(client)
        else:
            async with httpx.AsyncClient(timeout=30.0) as local_client:
                return await _do_search(local_client)

    async def chat(
        self,
        query: str,
        knowledge_base_id: str,
        headers: Optional[Dict[str, str]] = None,
        client: Optional[httpx.AsyncClient] = None
    ) -> Tuple[str, List[Dict[str, Any]]]:
        """
        Executes RAG-assisted chat scoped strictly to the authorized knowledge_base_id.
        Ensures prompts contain only chunks from the authorized Knowledge Base.
        Reuses application-scoped http_client when provided.
        """
        if client is not None:
            rag_result = await self.search_rag(query, knowledge_base_id, headers, client=client)
        else:
            rag_result = await self.search_rag(query, knowledge_base_id, headers)
        results = rag_result.get("results", [])

        context_chunks = []
        sources = []
        for item in results:
            doc = item.get("document", {})
            text = doc.get("text")
            if text:
                context_chunks.append(text)
            sources.append(doc)

        context_str = "\n\n---\n\n".join(context_chunks)

        prompt = f"""You are the OMRAGUL AI assistant.

Answer the user's question using the provided context.

Context:
{context_str}

User question:
{query}

If the context does not contain enough information, say that you do not have enough information."""

        response = await self.llm.ainvoke(prompt)
        return response.content, sources
