package com.omragul.processing.util;

import com.omragul.processing.exception.PermanentProcessingException;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * FilterInputStream that bounds the total number of bytes read from an underlying stream.
 * Immediately throws {@link PermanentProcessingException} ("FILE_TOO_LARGE") if the count
 * of actual bytes read exceeds the specified limit.
 */
public class BoundedInputStream extends FilterInputStream {

    private final long maxBytes;
    private final String documentContext;
    private long totalBytesRead = 0;

    public BoundedInputStream(InputStream in, long maxBytes, String documentContext) {
        super(in);
        this.maxBytes = maxBytes;
        this.documentContext = documentContext != null ? documentContext : "unknown";
    }

    public BoundedInputStream(InputStream in, long maxBytes) {
        this(in, maxBytes, "unknown");
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) {
            totalBytesRead++;
            if (maxBytes > 0 && totalBytesRead > maxBytes) {
                throw createLimitExceededException();
            }
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len <= 0) {
            return super.read(b, off, len);
        }
        int bytesToRead = len;
        if (maxBytes > 0) {
            long remaining = maxBytes - totalBytesRead;
            if (remaining >= 0 && remaining + 1 < bytesToRead) {
                bytesToRead = (int) (remaining + 1);
            }
        }
        int n = super.read(b, off, bytesToRead);
        if (n != -1) {
            totalBytesRead += n;
            if (maxBytes > 0 && totalBytesRead > maxBytes) {
                throw createLimitExceededException();
            }
        }
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 0) {
            return 0;
        }
        long toSkip = n;
        if (maxBytes > 0) {
            long remaining = maxBytes - totalBytesRead;
            if (remaining >= 0 && remaining + 1 < toSkip) {
                toSkip = remaining + 1;
            }
        }
        long skipped = super.skip(toSkip);
        if (skipped > 0) {
            totalBytesRead += skipped;
            if (maxBytes > 0 && totalBytesRead > maxBytes) {
                throw createLimitExceededException();
            }
        }
        return skipped;
    }

    public long getTotalBytesRead() {
        return totalBytesRead;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    private PermanentProcessingException createLimitExceededException() {
        return new PermanentProcessingException(
                "FILE_TOO_LARGE",
                String.format("Document %s exceeded maximum allowed size limit of %d bytes", documentContext, maxBytes)
        );
    }
}
