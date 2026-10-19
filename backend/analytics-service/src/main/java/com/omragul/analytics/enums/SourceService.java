package com.omragul.analytics.enums;

/**
 * Allowlisted source service identifiers.
 * Only these values may be submitted through the ingestion API.
 */
public enum SourceService {
    IDENTITY_SERVICE,
    KNOWLEDGE_SERVICE,
    PROCESSING_SERVICE,
    RAG_SERVICE,
    AGENT_SERVICE,
    MULTIMODAL_SERVICE,
    EMBEDDING_SERVICE
}
