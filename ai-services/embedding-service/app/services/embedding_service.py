import httpx
import logging

from app.config.settings import settings

logger = logging.getLogger(__name__)


class EmbeddingService:

    def __init__(self):
        self.base_url = settings.OLLAMA_BASE_URL
        self.model = settings.EMBEDDING_MODEL

    async def generate_embedding(self, text: str) -> list[float]:

        payload = {
            "model": self.model,
            "input": text
        }

        try:
            async with httpx.AsyncClient() as client:
                response = await client.post(
                    f"{self.base_url}/api/embed",
                    json=payload,
                    timeout=120.0
                )

            response.raise_for_status()

            data = response.json()

            return data["embeddings"][0]

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
        texts: list[str]
    ) -> list[list[float]]:

        payload = {
            "model": self.model,
            "input": texts
        }

        try:
            async with httpx.AsyncClient() as client:
                response = await client.post(
                    f"{self.base_url}/api/embed",
                    json=payload,
                    timeout=120.0
                )

            response.raise_for_status()

            data = response.json()

            return data["embeddings"]

        except httpx.ConnectError:
            raise RuntimeError("Unable to connect to Ollama")

        except httpx.TimeoutException:
            raise RuntimeError("Ollama request timed out")

        except httpx.HTTPStatusError as e:
            raise RuntimeError(
                f"Ollama returned HTTP {e.response.status_code}"
            )


    async def check_ollama(self) -> bool:

        try:
            async with httpx.AsyncClient() as client:
                response = await client.get(
                    f"{self.base_url}/api/tags",
                    timeout=5.0
                )

            response.raise_for_status()

            data = response.json()

            models = data.get("models", [])

            for model in models:
                if model.get("name", "").startswith(self.model):
                    return True

            return False

        except (httpx.ConnectError, httpx.TimeoutException):
            return False