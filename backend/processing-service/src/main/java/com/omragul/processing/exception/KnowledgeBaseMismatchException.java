package com.omragul.processing.exception;

import java.util.UUID;

public class KnowledgeBaseMismatchException extends RuntimeException {
    public KnowledgeBaseMismatchException(UUID requestedKbId, UUID documentKbId) {
        super(String.format("Requested knowledge_base_id %s does not match document's actual knowledge_base_id %s",
                requestedKbId, documentKbId));
    }

    public KnowledgeBaseMismatchException(String message) {
        super(message);
    }
}
