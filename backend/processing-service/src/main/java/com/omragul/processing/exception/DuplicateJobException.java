package com.omragul.processing.exception;

import java.util.UUID;

public class DuplicateJobException extends ProcessingException {
    private final UUID existingJobId;

    public DuplicateJobException(String message, UUID existingJobId) {
        super(message);
        this.existingJobId = existingJobId;
    }

    public UUID getExistingJobId() {
        return existingJobId;
    }
}
