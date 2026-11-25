package com.omragul.processing.exception;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(UUID documentId) {
        super(String.format("Document not found with ID: %s", documentId));
    }

    public DocumentNotFoundException(String message) {
        super(message);
    }
}
