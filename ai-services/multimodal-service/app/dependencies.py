import httpx
from fastapi import Request


def get_http_client(request: Request) -> httpx.AsyncClient:
    """
    FastAPI dependency to retrieve the application-scoped shared httpx.AsyncClient.
    Enforces lifespan management: fails closed if client is uninitialized or closed.
    Never creates unmanaged clients or returns closed clients in production paths.
    """
    client = getattr(request.app.state, "http_client", None)
    if client is None:
        raise RuntimeError("Application HTTP client is not initialized on app.state (lifespan unmanaged)")
    if client.is_closed:
        raise RuntimeError("Application HTTP client is closed on app.state")
    return client
