package com.omragul.processing.exception;

/**
 * Thrown when processing encounters a transient error that is eligible for retry
 * (e.g. downstream network timeout, temporary 503 service unavailable).
 */
public class TransientProcessingException extends ProcessingException {
    private final String errorCode;

    public TransientProcessingException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public TransientProcessingException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
