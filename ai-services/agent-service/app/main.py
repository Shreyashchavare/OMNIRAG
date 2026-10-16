from fastapi import FastAPI

from app.config.settings import settings
from app.api.routes import router


app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION
)


app.include_router(router)


@app.get("/health")
async def health():
    return {
        "status": "UP",
        "service": settings.APP_NAME
    }