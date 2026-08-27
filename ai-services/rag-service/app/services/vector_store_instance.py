from app.config.settings import settings
from app.services.vector_store import VectorStore


vector_store = VectorStore(
    dimension=settings.embedding_dimension
)