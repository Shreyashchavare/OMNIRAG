from fastapi import FastAPI

from app.config.settings import settings
from app.api.routes import router


app = FastAPI(
    title=settings.app_name,
    version=settings.app_version
)

app.include_router(
    router,
    prefix="/api/v1/rag"
)