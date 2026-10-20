package com.omragul.processing.exception;

/**
 * Thrown when processing encounters an unrecoverable failure that should not be retried
 * (e.g., document not found in Knowledge service, unsupported MIME type, validation failure).
 */
public class PermanentProcessingException extends ProcessingException {
    private final String errorCode;

    public PermanentProcessingException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public PermanentProcessingException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
