package com.omragul.identity.enums;

public enum PermissionType {

    // ==========================
    // USER MANAGEMENT
    // ==========================
    USER_CREATE,
    USER_READ,
    USER_UPDATE,
    USER_DELETE,

    USER_PROFILE_READ,
    USER_PROFILE_UPDATE,

    // ==========================
    // ROLE MANAGEMENT
    // ==========================
    ROLE_CREATE,
    ROLE_READ,
    ROLE_UPDATE,
    ROLE_DELETE,

    ROLE_ASSIGN,
    ROLE_REVOKE,

    // ==========================
    // PERMISSION MANAGEMENT
    // ==========================
    PERMISSION_CREATE,
    PERMISSION_READ,
    PERMISSION_UPDATE,
    PERMISSION_DELETE,

    PERMISSION_ASSIGN,
    PERMISSION_REVOKE,

    // ==========================
    // DOCUMENT MANAGEMENT
    // ==========================
    DOCUMENT_UPLOAD,
    DOCUMENT_READ,
    DOCUMENT_UPDATE,
    DOCUMENT_DELETE,

    DOCUMENT_DOWNLOAD,

    // ==========================
    // KNOWLEDGE BASE
    // ==========================
    KNOWLEDGE_CREATE,
    KNOWLEDGE_READ,
    KNOWLEDGE_UPDATE,
    KNOWLEDGE_DELETE,

    KNOWLEDGE_CONTENT_UPLOAD,

    // ==========================
    // EMBEDDINGS
    // ==========================
    EMBEDDING_GENERATE,
    EMBEDDING_DELETE,

    // ==========================
    // MULTIMODAL QUERY
    // ==========================
    MULTIMODAL_IMAGE_QUERY,
    MULTIMODAL_AUDIO_QUERY,
    MULTIMODAL_VIDEO_QUERY,

    // ==========================
    // MULTIMODAL PROCESSING
    // ==========================
    IMAGE_PROCESS,
    AUDIO_PROCESS,
    VIDEO_PROCESS,

    // ==========================
    // RAG
    // ==========================
    RAG_QUERY,

    // ==========================
    // AGENT
    // ==========================
    AGENT_EXECUTE,

    // ==========================
    // MODEL
    // ==========================
    MODEL_QUERY,
    MODEL_TEST,
    MODEL_EVALUATE,

    // ==========================
    // ANALYTICS
    // ==========================
    ANALYTICS_READ,

    // ==========================
    // NOTIFICATIONS
    // ==========================
    NOTIFICATION_SEND,

    // ==========================
    // APPROVAL
    // ==========================
    APPROVAL_READ,
    APPROVAL_APPROVE,
    APPROVAL_REJECT,

    // ==========================
    // SYSTEM
    // ==========================
    SYSTEM_SETTINGS,
    SYSTEM_MONITOR,
    SYSTEM_BACKUP
}