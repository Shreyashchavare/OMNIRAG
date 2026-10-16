import base64
import httpx

from app.config.settings import settings


class VisionService:

    @classmethod
    async def analyze_image(
        cls,
        image_bytes: bytes,
        prompt: str
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

        async with httpx.AsyncClient(timeout=120.0) as client:
            response = await client.post(
                f"{settings.OLLAMA_BASE_URL}/api/chat",
                json=payload
            )

            response.raise_for_status()

            data = response.json()

        return data["message"]["content"]