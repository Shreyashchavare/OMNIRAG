package com.omragul.processing.exception;

import java.util.UUID;

public class JobNotFoundException extends ProcessingException {
    public JobNotFoundException(UUID jobId) {
        super("Processing job not found with id: " + jobId);
    }

    public JobNotFoundException(String message) {
        super(message);
    }
}
