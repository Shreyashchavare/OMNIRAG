from typing import Optional
from pydantic import BaseModel, Field


class AgentRequest(BaseModel):
    query: str = Field(..., min_length=1)
    knowledge_base_id: str = Field(..., min_length=1)


class AgentResponse(BaseModel):
    response: str
    sources: list[dict] = []
