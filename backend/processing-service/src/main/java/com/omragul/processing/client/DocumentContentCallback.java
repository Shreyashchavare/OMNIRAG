package com.omragul.processing.client;

import java.io.IOException;
import java.io.InputStream;

/**
 * Functional callback interface for streaming document content directly from downstream response stream.
 */
@FunctionalInterface
public interface DocumentContentCallback<T> {

    /**
     * Consumes the binary document content stream with associated metadata.
     *
     * @param inputStream   active readable input stream of document binary content
     * @param contentType   MIME type reported by Knowledge Service
     * @param filename      original or safe filename
     * @param contentLength total content length in bytes (-1 if chunked/unknown)
     * @return callback result
     * @throws IOException on streaming or parsing failure
     */
    T doWithContent(InputStream inputStream, String contentType, String filename, long contentLength) throws IOException;
}
