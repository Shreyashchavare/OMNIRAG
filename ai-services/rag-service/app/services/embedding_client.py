import httpx

from app.config.settings import settings


class EmbeddingClient:

    @classmethod
    async def generate_embedding(cls, text: str) -> list[float]:

        payload = {
            "text": text
        }

        async with httpx.AsyncClient(timeout=60.0) as client:

            response = await client.post(
                f"{settings.embedding_service_url}/api/v1/embeddings",
                json=payload
            )

            response.raise_for_status()

            data = response.json()

        return data["embedding"]