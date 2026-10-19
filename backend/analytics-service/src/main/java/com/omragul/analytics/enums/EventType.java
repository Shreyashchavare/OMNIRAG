package com.omragul.analytics.enums;

/**
 * Allowlisted analytics event types.
 * Only these values may be submitted through the ingestion API.
 */
public enum EventType {

    // Document lifecycle
    DOCUMENT_UPLOADED,
    DOCUMENT_PROCESSED,
    DOCUMENT_FAILED,
    DOCUMENT_DELETED,

    // Query / RAG
    QUERY_EXECUTED,
    QUERY_SUCCEEDED,
    QUERY_FAILED,

    // AI / Model usage
    MODEL_USAGE,

    // User activity
    USER_LOGIN,
    USER_LOGOUT,
    USER_REGISTERED
}
