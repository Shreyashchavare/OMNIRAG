package com.omragul.knowledge.service;

import com.omragul.knowledge.entity.Document;

import java.io.IOException;
import java.io.InputStream;

/**
 * Service for safe, traversal-resistant physical storage access of document binaries.
 */
public interface DocumentStorageService {

    /**
     * Checks if physical storage content exists for the given document.
     */
    boolean exists(Document document);

    /**
     * Retrieves the file size in bytes for the stored document content.
     */
    long getFileSize(Document document);

    /**
     * Opens a readable InputStream to the stored document content.
     * Callers must close the returned stream.
     */
    InputStream openStream(Document document) throws IOException;
}
