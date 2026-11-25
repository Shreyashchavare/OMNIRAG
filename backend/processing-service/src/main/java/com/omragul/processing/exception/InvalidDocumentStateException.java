package com.omragul.processing.exception;

import java.util.UUID;

public class InvalidDocumentStateException extends RuntimeException {
    public InvalidDocumentStateException(UUID documentId, String reason) {
        super(String.format("Document %s is in an invalid state: %s", documentId, reason));
    }

    public InvalidDocumentStateException(String message) {
        super(message);
    }
}
