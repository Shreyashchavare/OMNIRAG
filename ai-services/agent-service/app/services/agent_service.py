import httpx

from langchain_ollama import ChatOllama

from app.config.settings import settings


class AgentService:

    def __init__(self):
        self.llm = ChatOllama(
            base_url=settings.OLLAMA_BASE_URL,
            model=settings.OLLAMA_MODEL
        )

    async def search_rag(self, query: str):

        async with httpx.AsyncClient() as client:

            response = await client.post(
                f"{settings.RAG_SERVICE_URL}/api/v1/rag/search",
                json={
                    "query": query
                }
            )

            response.raise_for_status()

            return response.json()

    async def chat(self, query: str) -> str:

        rag_result = await self.search_rag(query)

        prompt = f"""
You are the OMRAGUL AI assistant.

Answer the user's question using the provided context.

Context:
{rag_result}

User question:
{query}

If the context does not contain enough information,
say that you do not have enough information.
"""

        response = await self.llm.ainvoke(prompt)

        return response.content