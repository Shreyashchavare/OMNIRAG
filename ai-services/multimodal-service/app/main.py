from fastapi import FastAPI

from app.api.routes.multimodal_routes import router as multimodal_router
from app.config.settings import settings


app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description="Multimodal processing service for OMRAGUL"
)


app.include_router(multimodal_router)


@app.get("/health")
async def health_check():

    return {
        "status": "UP",
        "service": "multimodal-service"
    }