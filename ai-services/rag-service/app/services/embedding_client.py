import httpx
from typing import Optional

from app.config.settings import settings


class EmbeddingClient:

    @classmethod
    def _get_auth_headers(cls) -> dict[str, str]:
        secret = (
            getattr(settings, "internal_service_secret", None)
            or getattr(settings, "INTERNAL_SERVICE_SECRET", None)
        )
        if not secret or not str(secret).strip():
            raise ValueError("INTERNAL_SERVICE_SECRET is not configured for RAG EmbeddingClient")
        return {
            "X-Internal-Service-Secret": str(secret).strip(),
            "X-Internal-Caller-Service": "rag-service"
        }

    @classmethod
    async def generate_embedding(
        cls,
        text: str,
        client: Optional[httpx.AsyncClient] = None
    ) -> list[float]:
        headers = cls._get_auth_headers()
        payload = {
            "text": text
        }

        async def _do_post(http_client: httpx.AsyncClient) -> list[float]:
            post_kwargs = {
                "json": payload,
                "headers": headers
            }
            if client is not None:
                post_kwargs["timeout"] = 60.0

            response = await http_client.post(
                f"{settings.embedding_service_url}/api/v1/embeddings",
                **post_kwargs
            )
            response.raise_for_status()
            data = response.json()
            return data["embedding"]

        if client is not None:
            return await _do_post(client)
        else:
            async with httpx.AsyncClient(timeout=60.0) as local_client:
                return await _do_post(local_client)

    @classmethod
    async def generate_batch_embeddings(
        cls,
        texts: list[str],
        batch_size: int = 50,
        client: Optional[httpx.AsyncClient] = None
    ) -> list[list[float]]:
        """
        Generates dense vector embeddings for a list of texts using the batch embedding endpoint.
        Preserves chunk-to-embedding ordering strictly.
        Splits large batches into bounded sub-batches to prevent request payload bloat.
        """
        if not texts:
            return []

        headers = cls._get_auth_headers()
        cleaned_texts = [str(t) for t in texts]
        all_embeddings: list[list[float]] = []

        async def _process_batches(http_client: httpx.AsyncClient):
            for i in range(0, len(cleaned_texts), batch_size):
                sub_batch = cleaned_texts[i:i + batch_size]
                payload = {
                    "texts": sub_batch
                }

                response = await http_client.post(
                    f"{settings.embedding_service_url}/api/v1/embeddings/batch",
                    json=payload,
                    headers=headers,
                    timeout=60.0
                )
                response.raise_for_status()
                data = response.json()

                if "embeddings" not in data or not isinstance(data["embeddings"], list):
                    raise ValueError("Malformed batch embedding response: missing or invalid 'embeddings' field")

                batch_embeddings = data["embeddings"]
                if len(batch_embeddings) != len(sub_batch):
                    raise ValueError(
                        f"Mismatched embedding count: expected {len(sub_batch)}, received {len(batch_embeddings)}"
                    )

                expected_dim = getattr(settings, "embedding_dimension", None)
                for idx, emb in enumerate(batch_embeddings):
                    if not isinstance(emb, list) or len(emb) == 0:
                        raise ValueError(f"Malformed embedding vector at sub-batch index {idx}")
                    if expected_dim is not None and len(emb) != expected_dim:
                        raise ValueError(
                            f"Invalid embedding dimension at sub-batch index {idx}: expected {expected_dim}, got {len(emb)}"
                        )

                all_embeddings.extend(batch_embeddings)

        if client is not None:
            await _process_batches(client)
        else:
            async with httpx.AsyncClient(timeout=60.0) as local_client:
                await _process_batches(local_client)

        return all_embeddings
