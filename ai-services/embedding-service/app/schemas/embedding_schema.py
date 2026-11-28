from typing import Optional, Union
from pydantic import BaseModel, Field, model_validator


class EmbeddingRequest(BaseModel):
    text: Optional[str] = None
    input: Optional[str] = None

    @model_validator(mode="before")
    @classmethod
    def resolve_text_or_input(cls, data):
        if isinstance(data, dict):
            t = data.get("text") or data.get("input")
            if not t or not str(t).strip():
                raise ValueError("Either 'text' or 'input' must be provided and non-empty")
            data["text"] = str(t)
        return data


class BatchEmbeddingRequest(BaseModel):
    texts: Optional[list[str]] = None
    input: Optional[Union[list[str], str]] = None

    @model_validator(mode="before")
    @classmethod
    def resolve_texts_or_input(cls, data):
        if isinstance(data, dict):
            t = data.get("texts") or data.get("input")
            if isinstance(t, str):
                t = [t]
            if not t or len(t) == 0:
                raise ValueError("Either 'texts' or 'input' must be provided and non-empty")
            data["texts"] = [str(x) for x in t]
        return data
