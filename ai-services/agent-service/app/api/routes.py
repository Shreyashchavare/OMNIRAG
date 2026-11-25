import json
import uuid
import httpx
from fastapi import APIRouter, Depends, HTTPException, Request

from app.config.settings import settings
from app.dependencies import get_http_client
from app.models.schemas import AgentRequest, AgentResponse
from app.services.agent_service import AgentService
from app.services.auth_service import extract_and_verify_user_identity

router = APIRouter(
    prefix="/api/v1/agent",
    tags=["Agent"]
)

agent_service = AgentService()


@router.post("/chat", response_model=AgentResponse)
async def chat(
    request: AgentRequest,
    req: Request,
    http_client: httpx.AsyncClient = Depends(get_http_client)
):
    if not request.query or not request.query.strip():
        raise HTTPException(
            status_code=400,
            detail="Query cannot be empty"
        )

    kb_id = str(request.knowledge_base_id or "").strip()
    if not kb_id:
        raise HTTPException(
            status_code=400,
            detail="knowledge_base_id is required"
        )

    # 1. Enforce Gateway assertion authentication and anti-spoofing FIRST
    verified_identity = extract_and_verify_user_identity(req, settings.INTERNAL_SERVICE_SECRET)

    # 2. Validate knowledge_base_id format (reject whitespace, control chars, path traversal)
    if any(c in kb_id for c in (" ", "\t", "\n", "\r", "/", "\\", "..", "<", ">", "{", "}")):
        raise HTTPException(
            status_code=400,
            detail="Malformed knowledge_base_id: invalid characters detected"
        )

    # 2. Construct trusted downstream headers from verified context
    # Note: X-Internal-Service-Secret is strictly excluded from user-scoped search (Finding 2C.1-01)
    forward_headers = {
        "X-Gateway-Assertion": verified_identity.assertion_token,
        "X-User-Id": verified_identity.user_id,
        "X-Request-Id": verified_identity.request_id,
        "X-Internal-Caller-Service": "agent-service",
    }

    try:
        response_text, sources = await agent_service.chat(
            query=request.query,
            knowledge_base_id=request.knowledge_base_id,
            headers=forward_headers,
            client=http_client
        )

        return AgentResponse(
            response=response_text,
            sources=sources
        )
    except httpx.HTTPStatusError as e:
        status_code = e.response.status_code
        detail_msg = e.response.text
        try:
            parsed = e.response.json()
            if isinstance(parsed, dict) and "detail" in parsed:
                detail_msg = parsed["detail"]
        except Exception:
            pass
        raise HTTPException(
            status_code=status_code,
            detail=f"Downstream RAG service error: {detail_msg}"
        )
    except httpx.TimeoutException:
        raise HTTPException(
            status_code=504,
            detail="RAG service timeout"
        )
    except httpx.HTTPError as e:
        raise HTTPException(
            status_code=502,
            detail=f"RAG service connection error: {str(e)}"
        )
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Agent service processing error: {str(e)}"
        )
