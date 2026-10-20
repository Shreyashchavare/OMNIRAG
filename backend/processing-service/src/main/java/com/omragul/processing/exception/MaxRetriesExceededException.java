package com.omragul.processing.exception;

import java.util.UUID;

public class MaxRetriesExceededException extends ProcessingException {
    public MaxRetriesExceededException(UUID jobId, int attemptCount, int maxAttempts) {
        super(String.format("Job %s has reached maximum retry attempts (%d/%d) and cannot be retried",
                jobId, attemptCount, maxAttempts));
    }
}
