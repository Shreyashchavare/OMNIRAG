import base64
from typing import Optional
import httpx

from app.config.settings import settings


class VisionService:

    @classmethod
    async def analyze_image(
        cls,
        image_bytes: bytes,
        prompt: str,
        client: Optional[httpx.AsyncClient] = None
    ) -> str:

        image_base64 = base64.b64encode(image_bytes).decode("utf-8")

        payload = {
            "model": settings.OLLAMA_VISION_MODEL,
            "messages": [
                {
                    "role": "user",
                    "content": prompt,
                    "images": [image_base64]
                }
            ],
            "stream": False
        }

        async def _do_post(http_client: httpx.AsyncClient) -> str:
            response = await http_client.post(
                f"{settings.OLLAMA_BASE_URL}/api/chat",
                json=payload,
                timeout=120.0
            )
            response.raise_for_status()
            data = response.json()
            return data["message"]["content"]

        if client is not None:
            return await _do_post(client)
        else:
            async with httpx.AsyncClient(timeout=120.0) as local_client:
                return await _do_post(local_client)