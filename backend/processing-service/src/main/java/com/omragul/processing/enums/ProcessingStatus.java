package com.omragul.processing.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Valid lifecycle states for document processing jobs in OMRAGUL.
 */
public enum ProcessingStatus {
    QUEUED,
    VALIDATING,
    EXTRACTING,
    CHUNKING,
    EMBEDDING_INDEXING,
    COMPLETED,
    FAILED,
    CANCELLED;

    private static final Map<ProcessingStatus, Set<ProcessingStatus>> VALID_TRANSITIONS = Map.of(
            QUEUED, EnumSet.of(VALIDATING, CANCELLED, FAILED),
            VALIDATING, EnumSet.of(EXTRACTING, FAILED, CANCELLED),
            EXTRACTING, EnumSet.of(CHUNKING, FAILED, CANCELLED),
            CHUNKING, EnumSet.of(EMBEDDING_INDEXING, FAILED, CANCELLED),
            EMBEDDING_INDEXING, EnumSet.of(COMPLETED, FAILED, CANCELLED),
            FAILED, EnumSet.of(QUEUED), // Eligible for retry
            COMPLETED, EnumSet.noneOf(ProcessingStatus.class),
            CANCELLED, EnumSet.noneOf(ProcessingStatus.class)
    );

    public boolean canTransitionTo(ProcessingStatus target) {
        if (target == null) {
            return false;
        }
        return VALID_TRANSITIONS.getOrDefault(this, EnumSet.noneOf(ProcessingStatus.class)).contains(target);
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public boolean isActive() {
        return !isTerminal();
    }
}
