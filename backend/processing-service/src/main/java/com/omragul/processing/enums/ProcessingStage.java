package com.omragul.processing.enums;

/**
 * Granular processing stages indicating the subsystem currently executing.
 */
public enum ProcessingStage {
    INITIALIZATION,
    VALIDATION,
    EXTRACTION,
    CHUNKING,
    EMBEDDING,
    INDEXING,
    COMPLETED,
    FAILED,
    CANCELLED
}
