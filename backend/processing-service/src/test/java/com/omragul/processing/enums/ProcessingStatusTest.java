package com.omragul.processing.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProcessingStatusTest {

    @Test
    @DisplayName("Legal state transitions should be allowed")
    void testLegalTransitions() {
        assertTrue(ProcessingStatus.QUEUED.canTransitionTo(ProcessingStatus.VALIDATING));
        assertTrue(ProcessingStatus.QUEUED.canTransitionTo(ProcessingStatus.CANCELLED));
        assertTrue(ProcessingStatus.QUEUED.canTransitionTo(ProcessingStatus.FAILED));

        assertTrue(ProcessingStatus.VALIDATING.canTransitionTo(ProcessingStatus.EXTRACTING));
        assertTrue(ProcessingStatus.VALIDATING.canTransitionTo(ProcessingStatus.FAILED));
        assertTrue(ProcessingStatus.VALIDATING.canTransitionTo(ProcessingStatus.CANCELLED));

        assertTrue(ProcessingStatus.EXTRACTING.canTransitionTo(ProcessingStatus.CHUNKING));
        assertTrue(ProcessingStatus.EXTRACTING.canTransitionTo(ProcessingStatus.FAILED));

        assertTrue(ProcessingStatus.CHUNKING.canTransitionTo(ProcessingStatus.EMBEDDING_INDEXING));
        assertTrue(ProcessingStatus.CHUNKING.canTransitionTo(ProcessingStatus.FAILED));

        assertTrue(ProcessingStatus.EMBEDDING_INDEXING.canTransitionTo(ProcessingStatus.COMPLETED));
        assertTrue(ProcessingStatus.EMBEDDING_INDEXING.canTransitionTo(ProcessingStatus.FAILED));

        // Retry transition from FAILED to QUEUED
        assertTrue(ProcessingStatus.FAILED.canTransitionTo(ProcessingStatus.QUEUED));
    }

    @Test
    @DisplayName("Illegal state transitions should be rejected")
    void testIllegalTransitions() {
        assertFalse(ProcessingStatus.QUEUED.canTransitionTo(ProcessingStatus.COMPLETED));
        assertFalse(ProcessingStatus.QUEUED.canTransitionTo(ProcessingStatus.CHUNKING));
        assertFalse(ProcessingStatus.COMPLETED.canTransitionTo(ProcessingStatus.QUEUED));
        assertFalse(ProcessingStatus.COMPLETED.canTransitionTo(ProcessingStatus.FAILED));
        assertFalse(ProcessingStatus.CANCELLED.canTransitionTo(ProcessingStatus.QUEUED));
        assertFalse(ProcessingStatus.CANCELLED.canTransitionTo(ProcessingStatus.VALIDATING));
        assertFalse(ProcessingStatus.EXTRACTING.canTransitionTo(null));
    }

    @Test
    @DisplayName("Terminal and active status checks behave correctly")
    void testTerminalAndActiveChecks() {
        assertTrue(ProcessingStatus.COMPLETED.isTerminal());
        assertTrue(ProcessingStatus.FAILED.isTerminal());
        assertTrue(ProcessingStatus.CANCELLED.isTerminal());

        assertFalse(ProcessingStatus.QUEUED.isTerminal());
        assertFalse(ProcessingStatus.VALIDATING.isTerminal());
        assertFalse(ProcessingStatus.EXTRACTING.isTerminal());
        assertFalse(ProcessingStatus.CHUNKING.isTerminal());
        assertFalse(ProcessingStatus.EMBEDDING_INDEXING.isTerminal());

        assertTrue(ProcessingStatus.QUEUED.isActive());
        assertTrue(ProcessingStatus.VALIDATING.isActive());
        assertFalse(ProcessingStatus.COMPLETED.isActive());
    }
}
