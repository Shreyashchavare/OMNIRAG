from typing import Optional
import httpx
import logging

from app.config.settings import settings

logger = logging.getLogger(__name__)


class EmbeddingService:

    def __init__(self):
        self.base_url = settings.OLLAMA_BASE_URL
        self.model = settings.EMBEDDING_MODEL

    async def generate_embedding(
        self,
        text: str,
        client: Optional[httpx.AsyncClient] = None
    ) -> list[float]:

        payload = {
            "model": self.model,
            "input": text
        }

        async def _do_post(http_client: httpx.AsyncClient):
            response = await http_client.post(
                f"{self.base_url}/api/embed",
                json=payload,
                timeout=settings.OLLAMA_TIMEOUT
            )
            response.raise_for_status()
            data = response.json()
            return data["embeddings"][0]

        try:
            if client is not None:
                return await _do_post(client)
            else:
                async with httpx.AsyncClient() as local_client:
                    return await _do_post(local_client)

        except httpx.ConnectError:
            raise RuntimeError("Unable to connect to Ollama")

        except httpx.TimeoutException:
            raise RuntimeError("Ollama request timed out")

        except httpx.HTTPStatusError as e:
            raise RuntimeError(
                f"Ollama returned HTTP {e.response.status_code}"
            )

    async def generate_embeddings(
        self,
        texts: list[str],
        client: Optional[httpx.AsyncClient] = None
    ) -> list[list[float]]:

        payload = {
            "model": self.model,
            "input": texts
        }

        async def _do_post(http_client: httpx.AsyncClient):
            response = await http_client.post(
                f"{self.base_url}/api/embed",
                json=payload,
                timeout=settings.OLLAMA_TIMEOUT
            )
            response.raise_for_status()
            data = response.json()
            return data["embeddings"]

        try:
            if client is not None:
                return await _do_post(client)
            else:
                async with httpx.AsyncClient() as local_client:
                    return await _do_post(local_client)

        except httpx.ConnectError:
            raise RuntimeError("Unable to connect to Ollama")

        except httpx.TimeoutException:
            raise RuntimeError("Ollama request timed out")

        except httpx.HTTPStatusError as e:
            raise RuntimeError(
                f"Ollama returned HTTP {e.response.status_code}"
            )

    async def check_ollama(self, client: Optional[httpx.AsyncClient] = None) -> bool:

        async def _do_get(http_client: httpx.AsyncClient) -> bool:
            response = await http_client.get(
                f"{self.base_url}/api/tags",
                timeout=settings.OLLAMA_HEALTH_TIMEOUT
            )
            response.raise_for_status()
            data = response.json()
            models = data.get("models", [])
            for model in models:
                if model.get("name", "").startswith(self.model):
                    return True
            return False

        try:
            if client is not None:
                return await _do_get(client)
            else:
                async with httpx.AsyncClient() as local_client:
                    return await _do_get(local_client)

        except (httpx.ConnectError, httpx.TimeoutException, httpx.HTTPError):
            return False