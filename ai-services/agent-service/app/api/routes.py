from fastapi import APIRouter

from app.models.schemas import AgentRequest, AgentResponse
from app.services.agent_service import AgentService


router = APIRouter(
    prefix="/api/v1/agent",
    tags=["Agent"]
)

agent_service = AgentService()


@router.post("/chat", response_model=AgentResponse)
async def chat(request: AgentRequest):

    response = await agent_service.chat(request.query)

    return AgentResponse(
        response=response
    )