import logging
from typing import Optional
import httpx
from fastapi import APIRouter, Depends, HTTPException, Query, Request

from app.config.settings import settings
from app.dependencies import get_http_client
from app.models.schemas import (
    DeleteDocumentResponse,
    DeleteKnowledgeBaseResponse,
    IndexRequest,
    IndexResponse,
    QueryEmbeddingResponse,
    QueryRequest,
    SearchRequest,
    SearchResponse,
)
from app.services.auth_service import (
    extract_and_verify_user_identity,
    filter_active_documents,
    verify_gateway_or_internal_service,
    verify_internal_service_call,
    verify_knowledge_base_access,
)
from app.services.embedding_client import EmbeddingClient
from app.services.vector_store import (
    DimensionMismatchException,
    PersistenceException,
    ValidationException,
    VectorStoreException,
)
from app.services.vector_store_instance import vector_store

logger = logging.getLogger(__name__)

router = APIRouter()


@router.get("/health")
async def health():
    return {
        "status": "UP",
        "service": "rag-service"
    }


@router.post(
    "/query/embedding",
    response_model=QueryEmbeddingResponse
)
async def generate_query_embedding(
    request: QueryRequest,
    req: Request,
    http_client: httpx.AsyncClient = Depends(get_http_client)
):
    # Enforce authentication (Gateway assertion OR internal service secret)
    verify_gateway_or_internal_service(req, settings.internal_service_secret)

    try:
        if not request.query.strip():
            raise HTTPException(
                status_code=400,
                detail="Query cannot be empty"
            )

        embedding = await EmbeddingClient.generate_embedding(
            request.query,
            client=http_client
        )

        return QueryEmbeddingResponse(
            query=request.query,
            embedding=embedding,
            dimensions=len(embedding)
        )

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=502,
            detail=f"Unable to generate query embedding: {str(e)}"
        )


@router.post("/index", response_model=IndexResponse)
async def index_chunks(
    request: IndexRequest,
    req: Request,
    http_client: httpx.AsyncClient = Depends(get_http_client)
):
    # Enforce internal service authentication (only authorized processing-service may index)
    verify_internal_service_call(req, settings.internal_service_secret)

    try:
        clean_kb_id = str(request.knowledge_base_id).strip()
        if not clean_kb_id or clean_kb_id.lower() in ("default", "none", "null"):
            raise HTTPException(
                status_code=400,
                detail=f"Invalid knowledge_base_id: '{request.knowledge_base_id}'"
            )

        if not request.chunks:
            raise HTTPException(
                status_code=400,
                detail="Chunks cannot be empty"
            )

        # Retain only chunks that contain non-blank text, preserving chunk-to-text ordering
        valid_chunks = [
            chunk for chunk in request.chunks
            if chunk.get("text") and str(chunk.get("text")).strip()
        ]

        if not valid_chunks:
            raise HTTPException(
                status_code=400,
                detail="No valid chunks found"
            )

        # Reject cross-KB chunks
        for chunk in valid_chunks:
            chunk_kb = chunk.get("knowledge_base_id")
            if chunk_kb and str(chunk_kb).strip().lower() != clean_kb_id.lower():
                raise HTTPException(
                    status_code=400,
                    detail=f"Cross-KB chunk rejected: declared KB '{chunk_kb}' does not match request KB '{clean_kb_id}'"
                )

        # Prevent resurrection of soft-deleted documents by older processing jobs
        doc_ids = list({str(c["document_id"]) for c in valid_chunks if c.get("document_id") is not None})
        if doc_ids:
            active_ids = await filter_active_documents(
                knowledge_base_id=clean_kb_id,
                candidate_doc_ids=doc_ids,
                knowledge_service_url=settings.knowledge_service_url,
                internal_secret=settings.internal_service_secret,
                client=http_client
            )
            active_set = set(active_ids)
            inactive_ids = [did for did in doc_ids if did.lower() not in active_set]
            if inactive_ids:
                raise HTTPException(
                    status_code=409,
                    detail=f"Cannot index deleted or non-existent document(s): {inactive_ids}"
                )

        texts = [str(chunk["text"]) for chunk in valid_chunks]

        # Use batch embedding API rather than sequential single-chunk calls
        embeddings = await EmbeddingClient.generate_batch_embeddings(texts, client=http_client)

        if len(embeddings) != len(valid_chunks):
            raise HTTPException(
                status_code=502,
                detail=f"Mismatched embedding count: expected {len(valid_chunks)}, received {len(embeddings)}"
            )

        # Idempotent addition to the target Knowledge Base partition
        vector_store.add(
            embeddings=embeddings,
            documents=valid_chunks,
            knowledge_base_id=clean_kb_id
        )

        return IndexResponse(
            indexed=len(valid_chunks)
        )

    except HTTPException:
        raise
    except (ValidationException, DimensionMismatchException) as e:
        raise HTTPException(
            status_code=400,
            detail=f"Vector validation error: {str(e)}"
        )
    except PersistenceException as e:
        logger.error("Vector store persistence failure: %s", str(e))
        raise HTTPException(
            status_code=500,
            detail=f"Vector store persistence failure: {str(e)}"
        )
    except ValueError as e:
        raise HTTPException(
            status_code=502,
            detail=f"Batch embedding validation error: {str(e)}"
        )
    except httpx.HTTPError as e:
        raise HTTPException(
            status_code=502,
            detail=f"Downstream embedding service error: {str(e)}"
        )
    except Exception as e:
        logger.error("Unexpected error indexing chunks: %s", str(e), exc_info=True)
        raise HTTPException(
            status_code=500,
            detail=f"Unable to index chunks: {str(e)}"
        )


@router.post(
    "/search",
    response_model=SearchResponse
)
async def search(
    request: SearchRequest,
    req: Request,
    http_client: httpx.AsyncClient = Depends(get_http_client)
):
    # 1. Structural request validation
    if not request.query or not request.query.strip():
        raise HTTPException(
            status_code=400,
            detail="Query cannot be empty"
        )

    if not request.knowledge_base_id or not request.knowledge_base_id.strip():
        raise HTTPException(
            status_code=400,
            detail="knowledge_base_id is required"
        )

    if request.top_k <= 0:
        raise HTTPException(
            status_code=400,
            detail="top_k must be greater than 0"
        )

    # 2. Authenticate user identity (from Gateway assertion ONLY)
    verified_identity = extract_and_verify_user_identity(req, settings.internal_service_secret)
    user_id = verified_identity.user_id
    gateway_assertion = verified_identity.assertion_token
    request_id = verified_identity.request_id

    # 3. Authorize user READ access for the requested Knowledge Base via Knowledge Service
    is_authorized = await verify_knowledge_base_access(
        user_id=user_id,
        knowledge_base_id=request.knowledge_base_id,
        knowledge_service_url=settings.knowledge_service_url,
        internal_secret=settings.internal_service_secret,
        client=http_client,
        gateway_assertion=gateway_assertion,
        request_id=request_id
    )
    if not is_authorized:
        raise HTTPException(
            status_code=403,
            detail=f"Access denied to knowledge base: {request.knowledge_base_id}"
        )

    # 4. Generate query embedding
    try:
        query_embedding = await EmbeddingClient.generate_embedding(
            request.query,
            client=http_client
        )
    except Exception as e:
        logger.error("Failed to generate query embedding: %s", str(e))
        raise HTTPException(
            status_code=502,
            detail="Failed to generate query embedding from embedding service"
        )

    # 5. Search strictly within the authorized KB partition (Guarantees zero cross-KB leakage)
    try:
        results = vector_store.search(
            query_embedding=query_embedding,
            knowledge_base_id=request.knowledge_base_id,
            top_k=request.top_k
        )

        # 6. Authoritatively filter out soft-deleted documents via Knowledge Service
        candidate_doc_ids = list({
            str(r["document"]["document_id"])
            for r in results
            if r.get("document", {}).get("document_id")
        })
        if candidate_doc_ids:
            active_ids = await filter_active_documents(
                knowledge_base_id=request.knowledge_base_id,
                candidate_doc_ids=candidate_doc_ids,
                knowledge_service_url=settings.knowledge_service_url,
                internal_secret=settings.internal_service_secret,
                client=http_client
            )
            active_set = set(active_ids)
            results = [
                r for r in results
                if str(r.get("document", {}).get("document_id", "")).lower() in active_set
            ]

        return SearchResponse(
            results=results
        )

    except (ValidationException, DimensionMismatchException) as e:
        raise HTTPException(
            status_code=400,
            detail=f"Search parameter error: {str(e)}"
        )
    except Exception as e:
        logger.error("Error executing vector search for KB %s: %s", request.knowledge_base_id, str(e))
        raise HTTPException(
            status_code=500,
            detail=f"Unable to search vector store: {str(e)}"
        )


@router.delete(
    "/documents/{document_id}",
    response_model=DeleteDocumentResponse
)
async def delete_document(
    document_id: str,
    req: Request,
    knowledge_base_id: Optional[str] = Query(None)
):
    """
    Physically removes all vectors and chunks belonging to document_id.
    Can be scoped to a specific knowledge_base_id or executed across all partitions.
    """
    # Enforce internal service authentication (only authorized internal services may delete)
    verify_internal_service_call(req, settings.internal_service_secret)

    try:
        deleted_count = vector_store.delete_document(
            document_id=document_id,
            knowledge_base_id=knowledge_base_id
        )

        return DeleteDocumentResponse(
            document_id=document_id,
            knowledge_base_id=knowledge_base_id,
            deleted_chunks=deleted_count
        )

    except PersistenceException as e:
        raise HTTPException(
            status_code=500,
            detail=f"Failed to persist index after document deletion: {str(e)}"
        )
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Failed to delete document vectors: {str(e)}"
        )


@router.delete(
    "/knowledge-bases/{knowledge_base_id}",
    response_model=DeleteKnowledgeBaseResponse
)
async def delete_knowledge_base(
    knowledge_base_id: str,
    req: Request
):
    """
    Purges an entire Knowledge Base partition and removes its stored snapshots.
    """
    # Enforce internal service authentication (only authorized internal services may purge)
    verify_internal_service_call(req, settings.internal_service_secret)

    try:
        deleted = vector_store.delete_knowledge_base(knowledge_base_id)

        return DeleteKnowledgeBaseResponse(
            knowledge_base_id=knowledge_base_id,
            deleted=deleted
        )

    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Failed to purge knowledge base partition: {str(e)}"
        )
